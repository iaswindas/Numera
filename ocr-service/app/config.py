"""OCR Service configuration — supports both VLM and PaddleOCR backends."""

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class OcrSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="OCR_")

    host: str = "0.0.0.0"
    port: int = 8001
    debug: bool = False

    # Internal API key (must match backend's configured key)
    api_key: str = ""

    # Storage
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket: str = "numera-documents"
    minio_secure: bool = False

    # --- Processor backend ---
    # "qwen3vl" = Qwen3-VL-8B (SOTA VLM, single model does everything)
    # "paddleocr" = Legacy PaddleOCR + PP-Structure pipeline (fallback)
    processor_backend: str = "qwen3vl"

    # --- Qwen3-VL settings ---
    vlm_model_id: str = "Qwen/Qwen3-VL-8B-Instruct"
    vlm_model_path: str = ""  # Local path to fine-tuned model (overrides model_id)
    vlm_quantize: bool = True  # 4-bit quantization (fits on T4 16GB)
    vlm_device: str = "auto"  # "auto", "cpu", "cuda", "cuda:0"
    vlm_max_new_tokens: int = 4096

    # --- PaddleOCR settings (legacy fallback) ---
    lang: str = "en"
    use_gpu: bool = False
    dpi: int = 300
    supported_languages: str = "en,ar,fr,zh"
    enable_language_auto_detect: bool = True

    # --- Image pre-processing ---
    enable_despeckle_low: bool = True
    enable_despeckle_high: bool = True
    enable_deskew: bool = True
    enable_watermark_removal: bool = True

    # Table Detection
    table_confidence_threshold: float = 0.5

    # --- Phase 1: STGH fingerprinting ---
    enable_stgh: bool = False
    stgh_hash_bits: int = 256
    stgh_k_neighbors: int = 6
    stgh_gcn_hidden: int = 128
    stgh_gcn_output: int = 256
    stgh_semantic_dim: int = 128
    stgh_similarity_threshold: float = 0.85
    stgh_use_semantic_model: bool = False

    # CORS
    cors_origins: str = "*"

    # Local fallback for demo (bypass MinIO)
    local_storage_path: str = ""

    @model_validator(mode="after")
    def validate_security(self):
        if not self.debug:
            if not self.api_key.strip():
                raise ValueError("OCR_API_KEY must be configured")
            if not self.minio_access_key.strip() or not self.minio_secret_key.strip():
                raise ValueError("OCR_MINIO_ACCESS_KEY and OCR_MINIO_SECRET_KEY must be configured")
        return self

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",")]

    @property
    def use_vlm(self) -> bool:
        return self.processor_backend in ("qwen3vl", "vlm")

    @property
    def supported_language_list(self) -> list[str]:
        langs = [lang.strip().lower() for lang in self.supported_languages.split(",") if lang.strip()]
        return langs or ["en"]


settings = OcrSettings()
