# Numera ML Training

All ML training, fine-tuning, data processing, and model evaluation runs on **Google Colab** (free T4 GPU) or **Kaggle Notebooks** (30 hrs/week free GPU).

The local machine is used **only** for inference inside Docker containers.

## Notebook Order

| # | Notebook | Purpose | Phase |
|---|----------|---------|-------|
| 00 | `00_environment_setup.ipynb` | Install deps, mount Drive, check GPU | 0 |
| 01 | `01_edgar_data_collection.ipynb` | Download SEC EDGAR 10-K filings | 0 |
| 02 | `02_lse_gcc_data_collection.ipynb` | Download LSE and GCC annual reports | 0 |
| 03 | `03_xbrl_parsing_autolabeling.ipynb` | Parse XBRL for auto-labeling | 0 |
| 04 | `04_ocr_batch_processing.ipynb` | Run PaddleOCR on all collected PDFs | 0 |
| 05 | `05_table_extraction_eval.ipynb` | Evaluate PP-Structure table detection | 0 |
| 06 | `06_zone_annotation_tool.ipynb` | Manual zone label annotation UI | 0 |
| 07 | `07_layoutlm_zone_training.ipynb` | Fine-tune LayoutLM zone classifier | 0 |
| 08 | `08_sbert_baseline_eval.ipynb` | Evaluate pre-trained Sentence-BERT | 0 |
| 09 | `09_sbert_finetuning.ipynb` | Fine-tune SBERT on IFRS pairs | 0 |
| 10 | `10_ifrs_taxonomy_builder.ipynb` | Build IFRS synonym taxonomy | 0 |
| 11 | `11_model_evaluation_report.ipynb` | Generate final evaluation report | 0 |
| 12 | `12_export_to_mlflow.ipynb` | Export models to MLflow registry | 0 |
| 20 | `20_feedback_retraining.ipynb` | Retrain from user corrections | 1 |
| 21 | `21_client_model_specialization.ipynb` | Per-client model fine-tuning | 1 |

## Setup

1. Open any notebook in Google Colab
2. Run `00_environment_setup.ipynb` first to install dependencies
3. Mount Google Drive to persist data between sessions
4. Configure MLflow tracking URI (see `configs/colab_secrets.yaml.template`)

## Reusable Scripts

The `scripts/` directory contains Python modules imported by notebooks:
- `edgar_downloader.py` — SEC EDGAR filing downloader
- `xbrl_parser.py` — XBRL financial fact parser
- `data_splitter.py` — Stratified train/val/test splitting
- `evaluation_utils.py` — Metrics computation and reporting
