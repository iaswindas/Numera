import os
import json
from pathlib import Path

notebooks_dir = Path("ml-training/notebooks")
notebooks_dir.mkdir(parents=True, exist_ok=True)

notebooks = {
    "00_environment_setup.ipynb": [
        "!pip install -q paddlepaddle-gpu paddleocr paddlex transformers sentence-transformers torch torchvision datasets evaluate scikit-learn mlflow python-xbrl arelle PyMuPDF Pillow streamlit plotly wandb",
        "from google.colab import drive\ndrive.mount('/content/drive')",
        "import torch\nprint(f'GPU: {torch.cuda.get_device_name(0)}')\nprint(f'VRAM: {torch.cuda.get_device_properties(0).total_mem / 1e9:.1f} GB')",
        "import mlflow\nmlflow.set_tracking_uri('https://dagshub.com/<user>/numera-ml.mlflow')\nos.environ['MLFLOW_TRACKING_USERNAME'] = '<token>'\nos.environ['MLFLOW_TRACKING_PASSWORD'] = '<token>'"
    ],
    "01_edgar_data_collection.ipynb": [
        "import requests\nimport time\nimport json\nfrom pathlib import Path\n# Add collection logic here..."
    ],
    "02_lse_gcc_data_collection.ipynb": [
        "# LSE & GCC Data Collection Scripts\n# Add scraping logic here..."
    ],
    "03_xbrl_parsing_autolabeling.ipynb": [
        "from arelle import Cntlr\n# Add XBRL parsing logic here..."
    ],
    "04_ocr_batch_processing.ipynb": [
        "from paddleocr import PaddleOCR\nfrom ppstructure import PPStructure\n# Add OCR batch processing here..."
    ],
    "06_zone_annotation_tool.ipynb": [
        "import ipywidgets as widgets\nfrom IPython.display import display, Image\nimport json\n# Add annotation logic here..."
    ],
    "07_layoutlm_zone_training.ipynb": [
        "import mlflow\nfrom transformers import LayoutLMForSequenceClassification, LayoutLMTokenizer, TrainingArguments, Trainer, EarlyStoppingCallback\n# Add LayoutLM logic here..."
    ],
    "08_sbert_baseline_eval.ipynb": [
        "from sentence_transformers import SentenceTransformer\nfrom sklearn.metrics.pairwise import cosine_similarity\nimport numpy as np\n# Add evaluation logic here..."
    ],
    "09_sbert_finetuning.ipynb": [
        "from sentence_transformers import SentenceTransformer, InputExample, losses, evaluation, SentenceTransformerTrainer, SentenceTransformerTrainingArguments\n# Add fine-tuning logic here..."
    ],
    "10_ifrs_taxonomy_builder.ipynb": [
        "taxonomy = {}\n# Add taxonomy building logic here..."
    ],
    "12_export_to_mlflow.ipynb": [
        "import mlflow\nfrom mlflow.tracking import MlflowClient\n# Add export logic here..."
    ]
}

def create_notebook(filename, cells_code):
    cells = []
    for code in cells_code:
        cells.append({
            "cell_type": "code",
            "execution_count": None,
            "metadata": {},
            "outputs": [],
            "source": [line + "\n" for line in code.split("\n")]
        })
    nb = {
        "cells": cells,
        "metadata": {},
        "nbformat": 4,
        "nbformat_minor": 5
    }
    with open(notebooks_dir / filename, "w", encoding="utf-8") as f:
        json.dump(nb, f, indent=2)

for filename, cells_code in notebooks.items():
    create_notebook(filename, cells_code)
    print(f"Created {filename}")
