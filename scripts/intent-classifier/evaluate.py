#!/usr/bin/env python3
"""Evaluate prototype-based intent classification with an exported ONNX encoder."""

from __future__ import annotations

import argparse
import csv
import json
import os
from collections import Counter
from pathlib import Path
from typing import Iterable


LOCAL_HF_CACHE = Path(__file__).resolve().parent / ".cache" / "huggingface"
os.environ.setdefault("HF_HOME", LOCAL_HF_CACHE.as_posix())

MAX_LENGTH = 128
BATCH_SIZE = 32
REPORT_PATH = Path("eval_report.md")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate intent prototypes against an independent JSONL or CSV test set."
    )
    parser.add_argument("--test", required=True, help="Test dataset path, JSONL or CSV.")
    parser.add_argument("--prototypes", required=True, help="Path to model/v1/prototypes.json.")
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


def load_prototypes(path: Path) -> tuple[list[str], np.ndarray, int]:
    import numpy as np

    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)

    labels = []
    vectors = []
    for item in data.get("intents", []):
        labels.append(str(item["label"]))
        vectors.append(item["vector"])

    if not labels:
        raise ValueError(f"No intents found in {path}")

    vector_dim = int(data["vector_dim"])
    matrix = np.asarray(vectors, dtype=np.float32)
    if matrix.shape != (len(labels), vector_dim):
        raise ValueError(
            f"Prototype matrix shape {matrix.shape} does not match vector_dim={vector_dim}."
        )
    return labels, l2_normalize(matrix), vector_dim


def l2_normalize(matrix: np.ndarray) -> np.ndarray:
    import numpy as np

    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    norms = np.maximum(norms, 1e-12)
    return matrix / norms


def encode_onnx(
    texts: list[str],
    tokenizer,
    session,
    batch_size: int = BATCH_SIZE,
) -> np.ndarray:
    import numpy as np

    embeddings = []
    output_name = session.get_outputs()[0].name

    for start in range(0, len(texts), batch_size):
        batch = texts[start : start + batch_size]
        encoded = tokenizer(
            batch,
            padding=True,
            truncation=True,
            max_length=MAX_LENGTH,
            return_tensors="np",
        )
        feeds = {
            "input_ids": encoded["input_ids"].astype(np.int64),
            "attention_mask": encoded["attention_mask"].astype(np.int64),
        }
        output = session.run([output_name], feeds)[0]
        cls_vectors = output[:, 0, :] if output.ndim == 3 else output
        embeddings.append(cls_vectors.astype(np.float32))

    return l2_normalize(np.vstack(embeddings))


def compute_metrics(
    true_labels: list[str], predicted_labels: list[str], known_labels: list[str]
) -> tuple[float, list[dict]]:
    correct = sum(1 for true, pred in zip(true_labels, predicted_labels, strict=True) if true == pred)
    accuracy = correct / len(true_labels)

    label_set = sorted(set(known_labels) | set(true_labels) | set(predicted_labels))
    true_counts = Counter(true_labels)
    pred_counts = Counter(predicted_labels)
    true_positive = Counter(
        true
        for true, pred in zip(true_labels, predicted_labels, strict=True)
        if true == pred
    )

    rows = []
    for label in label_set:
        tp = true_positive[label]
        precision = tp / pred_counts[label] if pred_counts[label] else 0.0
        recall = tp / true_counts[label] if true_counts[label] else 0.0
        f1 = (
            2 * precision * recall / (precision + recall)
            if precision + recall > 0
            else 0.0
        )
        rows.append(
            {
                "label": label,
                "support": true_counts[label],
                "precision": precision,
                "recall": recall,
                "f1": f1,
            }
        )
    return accuracy, rows


def render_report(accuracy: float, metrics: list[dict]) -> str:
    weak = [row for row in metrics if row["support"] > 0 and row["f1"] < 0.7]
    lines = [
        "# Intent Classifier Evaluation",
        "",
        f"- Accuracy: {accuracy:.4f}",
        f"- Weak intents (F1 < 0.7): {', '.join(row['label'] for row in weak) if weak else 'None'}",
        "",
        "| Intent | Support | Precision | Recall | F1 |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for row in metrics:
        lines.append(
            f"| {row['label']} | {row['support']} | {row['precision']:.4f} | "
            f"{row['recall']:.4f} | {row['f1']:.4f} |"
        )
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    args = parse_args()

    import onnxruntime as ort
    from transformers import AutoTokenizer

    test_path = Path(args.test)
    prototypes_path = Path(args.prototypes)
    model_dir = prototypes_path.parent
    encoder_path = model_dir / "encoder.onnx"
    tokenizer_dir = model_dir / "tokenizer"

    if not encoder_path.exists():
        raise FileNotFoundError(f"ONNX encoder not found: {encoder_path}")
    if not tokenizer_dir.exists():
        raise FileNotFoundError(f"Tokenizer directory not found: {tokenizer_dir}")

    rows = read_dataset(test_path)
    labels, prototypes, _ = load_prototypes(prototypes_path)
    tokenizer = AutoTokenizer.from_pretrained(tokenizer_dir.as_posix())
    session = ort.InferenceSession(
        encoder_path.as_posix(), providers=["CPUExecutionProvider"]
    )

    texts = [query for query, _ in rows]
    true_labels = [label for _, label in rows]
    embeddings = encode_onnx(texts, tokenizer, session)
    scores = embeddings @ prototypes.T
    predicted_labels = [labels[index] for index in scores.argmax(axis=1).tolist()]

    accuracy, metrics = compute_metrics(true_labels, predicted_labels, labels)
    report = render_report(accuracy, metrics)
    REPORT_PATH.write_text(report, encoding="utf-8")

    print(report)
    print(f"Wrote {REPORT_PATH}")


if __name__ == "__main__":
    main()
