# Intent Classifier Scripts

Offline scripts for building and evaluating the local intent classifier used by `show-engine`.

These scripts are development utilities. They are not runtime Agent tools.

## Files

```text
scripts/intent-classifier/
  train.py          Build prototype vectors and export encoder.onnx
  evaluate.py       Evaluate prototypes against a held-out dataset
  requirements.txt  Python dependencies for training/evaluation
```

## Inputs and Outputs

Expected datasets:

```text
data/
  training_data.csv
  test_set.csv
```

Generated model artifacts:

```text
model/v1/
  encoder.onnx
  prototypes.json
  tokenizer/
```

## Setup

From the repository root:

```bash
python3 -m venv .venv
. .venv/bin/activate
pip install -r scripts/intent-classifier/requirements.txt
```

## Train

```bash
cd scripts/intent-classifier
python train.py --train ../../data/training_data.csv --output-dir ../../model/v1
```

## Evaluate

```bash
cd scripts/intent-classifier
python evaluate.py --test ../../data/test_set.csv --prototypes ../../model/v1/prototypes.json
```

`evaluate.py` writes `eval_report.md` in the current working directory.
