#!/usr/bin/env python3
"""Build intent prototypes and export the bge-small-zh encoder to ONNX.

ONNX contract for Java/DJL integration:
  inputs:
    - input_ids: int64 tensor shaped [batch, sequence]
    - attention_mask: int64 tensor shaped [batch, sequence]
  outputs:
    - last_hidden_state: float32 tensor shaped [batch, sequence, 384]

The intent embedding used by this pipeline is the L2-normalized [CLS] vector:
last_hidden_state[:, 0, :].

Note: BAAI/bge-small-zh exposes a 512-dimensional hidden state in current
Transformers releases. The Java integration contract for this project requires
384-dimensional vectors, so this offline pipeline consistently keeps the first
384 hidden dimensions before L2 normalization and ONNX export.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
from collections import defaultdict
from pathlib import Path
from typing import Iterable


LOCAL_HF_CACHE = Path(__file__).resolve().parent / ".cache" / "huggingface"
os.environ.setdefault("HF_HOME", LOCAL_HF_CACHE.as_posix())

MODEL_ID = "BAAI/bge-small-zh"
MODEL_NAME = "bge-small-zh"
VERSION = "v1"
VECTOR_DIM = 384
MAX_LENGTH = 128
BATCH_SIZE = 32


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Encode a labeled intent dataset into normalized prototype vectors."
    )
    parser.add_argument("--train", required=True, help="Training dataset path, JSONL or CSV.")
    parser.add_argument(
        "--output-dir",
        required=True,
        help="Output directory. Use model/v1 for the Java integration contract.",
    )
    return parser.parse_args()


def read_dataset(path: Path) -> list[tuple[str, str]]:
    if not path.exists():
        raise FileNotFoundError(f"Dataset not found: {path}")

    suffix = path.suffix.lower()
    if suffix == ".jsonl":
        rows = read_jsonl(path)
    elif suffix == ".csv":
        rows = read_csv(path)
    else:
        raise ValueError(f"Unsupported dataset format: {path.suffix}. Use .jsonl or .csv.")

    cleaned: list[tuple[str, str]] = []
    for line_no, row in rows:
        query = str(row.get("query", "")).strip()
        label = str(row.get("intent_label", "")).strip()
        if not query or not label:
            raise ValueError(
                f"{path}:{line_no} must contain non-empty 'query' and 'intent_label'."
            )
        cleaned.append((query, label))

    if not cleaned:
        raise ValueError(f"Dataset is empty: {path}")
    return cleaned


def read_jsonl(path: Path) -> Iterable[tuple[int, dict]]:
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                row = json.loads(stripped)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_no} is not valid JSON.") from exc
            if not isinstance(row, dict):
                raise ValueError(f"{path}:{line_no} must be a JSON object.")
            yield line_no, row


def read_csv(path: Path) -> Iterable[tuple[int, dict]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            raise ValueError(f"{path} has no CSV header.")
        missing = {"query", "intent_label"} - set(reader.fieldnames)
        if missing:
            raise ValueError(f"{path} is missing required columns: {sorted(missing)}")
        for line_no, row in enumerate(reader, start=2):
            yield line_no, row


def encode_texts(
    texts: list[str],
    tokenizer,
    model,
    device,
    batch_size: int = BATCH_SIZE,
):
    import torch
    import torch.nn.functional as F

    vectors: list[torch.Tensor] = []
    model.eval()

    with torch.inference_mode():
        for start in range(0, len(texts), batch_size):
            batch = texts[start : start + batch_size]
            encoded = tokenizer(
                batch,
                padding=True,
                truncation=True,
                max_length=MAX_LENGTH,
                return_tensors="pt",
            )
            input_ids = encoded["input_ids"].to(device)
            attention_mask = encoded["attention_mask"].to(device)
            outputs = model(input_ids=input_ids, attention_mask=attention_mask)
            hidden_state = outputs.last_hidden_state
            if hidden_state.shape[-1] < VECTOR_DIM:
                raise ValueError(
                    f"Model hidden size {hidden_state.shape[-1]} is smaller than "
                    f"required vector_dim={VECTOR_DIM}."
                )
            cls_vectors = hidden_state[:, 0, :VECTOR_DIM]
            vectors.append(F.normalize(cls_vectors, p=2, dim=1).cpu())

    return torch.cat(vectors, dim=0)


def build_prototypes(rows: list[tuple[str, str]], embeddings) -> dict:
    import torch
    import torch.nn.functional as F

    by_label: dict[str, list[torch.Tensor]] = defaultdict(list)
    for (_, label), vector in zip(rows, embeddings, strict=True):
        by_label[label].append(vector)

    intents = []
    for label in sorted(by_label):
        stacked = torch.stack(by_label[label], dim=0)
        prototype = F.normalize(stacked.mean(dim=0, keepdim=True), p=2, dim=1).squeeze(0)
        if prototype.numel() != VECTOR_DIM:
            raise ValueError(
                f"Unexpected vector dimension for {label}: {prototype.numel()}, "
                f"expected {VECTOR_DIM}."
            )
        intents.append(
            {
                "label": label,
                "vector": [round(float(value), 10) for value in prototype.tolist()],
            }
        )

    return {
        "model": MODEL_NAME,
        "version": VERSION,
        "vector_dim": VECTOR_DIM,
        "intents": intents,
    }


def export_onnx(model, tokenizer, output_path: Path, device) -> None:
    import torch
    import torch.nn as nn

    class EncoderOnnxWrapper(nn.Module):
        def __init__(self, base_model: nn.Module):
            super().__init__()
            self.model = base_model

        def forward(
            self, input_ids: torch.Tensor, attention_mask: torch.Tensor
        ) -> torch.Tensor:
            outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
            return outputs.last_hidden_state[:, :, :VECTOR_DIM]

    sample = tokenizer(
        ["你好"],
        padding=True,
        truncation=True,
        max_length=MAX_LENGTH,
        return_tensors="pt",
    )
    wrapper = EncoderOnnxWrapper(model).to(device).eval()
    input_ids = sample["input_ids"].to(device)
    attention_mask = sample["attention_mask"].to(device)

    torch.onnx.export(
        wrapper,
        (input_ids, attention_mask),
        output_path.as_posix(),
        input_names=["input_ids", "attention_mask"],
        output_names=["last_hidden_state"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "sequence"},
            "attention_mask": {0: "batch", 1: "sequence"},
            "last_hidden_state": {0: "batch", 1: "sequence"},
        },
        opset_version=17,
        do_constant_folding=True,
    )


def main() -> None:
    args = parse_args()
    import torch
    from transformers import AutoModel, AutoTokenizer

    train_path = Path(args.train)
    output_dir = Path(args.output_dir)
    tokenizer_dir = output_dir / "tokenizer"
    output_dir.mkdir(parents=True, exist_ok=True)
    tokenizer_dir.mkdir(parents=True, exist_ok=True)

    rows = read_dataset(train_path)
    texts = [query for query, _ in rows]

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    model = AutoModel.from_pretrained(MODEL_ID).to(device)

    embeddings = encode_texts(texts, tokenizer, model, device)
    prototypes = build_prototypes(rows, embeddings)

    prototypes_path = output_dir / "prototypes.json"
    with prototypes_path.open("w", encoding="utf-8") as handle:
        json.dump(prototypes, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    export_onnx(model, tokenizer, output_dir / "encoder.onnx", device)
    tokenizer.save_pretrained(tokenizer_dir)

    print(f"Wrote {prototypes_path}")
    print(f"Wrote {output_dir / 'encoder.onnx'}")
    print(f"Wrote {tokenizer_dir}")


if __name__ == "__main__":
    main()
