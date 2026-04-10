"""OCR Service configuration — supports both VLM and PaddleOCR backends."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class OcrSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="OCR_")

    host: str = "0.0.0.0"
    port: int = 8001
    debug: bool = False

    # Storage
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
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

    # Table Detection
    table_confidence_threshold: float = 0.5

    # CORS
    cors_origins: str = "*"

    # Local fallback for demo (bypass MinIO)
    local_storage_path: str = ""

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",")]

    @property
    def use_vlm(self) -> bool:
        return self.processor_backend in ("qwen3vl", "vlm")


settings = OcrSettings()
