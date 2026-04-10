"""ML Service configuration via pydantic-settings."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class MlSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ML_")

    host: str = "0.0.0.0"
    port: int = 8002
    debug: bool = False

    # Storage
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"

    # MLflow
    mlflow_uri: str = "http://localhost:5000"
    layoutlm_model_name: str = "layoutlm-zone-classifier"
    layoutlm_model_version: str = "latest"  # or "Production"
    sbert_model_name: str = "sbert-ifrs-matcher"
    sbert_model_version: str = "latest"

    # Inference
    device: str = "cpu"
    mapping_high_confidence: float = 0.85
    mapping_medium_confidence: float = 0.65

    # CORS
    cors_origins: str = "*"

    # Model cache
    model_cache_dir: str = "/app/models"

    # HuggingFace fallback models (pre-training development)
    sbert_hf_fallback: str = "BAAI/bge-small-en-v1.5"
    layoutlm_hf_fallback: str = "microsoft/layoutlm-base-uncased"

    # --- Phase 1: PostgreSQL (feedback persistence) ---
    pg_host: str = "localhost"
    pg_port: int = 5432
    pg_database: str = "numera_ml"
    pg_user: str = "numera"
    pg_password: str = "numera"
    pg_min_pool: int = 2
    pg_max_pool: int = 10

    # --- Phase 1: A/B Testing ---
    ab_test_staging_ratio: float = 0.10  # 10% of requests go to Staging model
    ab_test_enabled: bool = False  # Toggle A/B testing on/off

    # --- Phase 1: Per-Client Models ---
    enable_client_models: bool = False
    client_model_min_corrections: int = 100  # Min corrections before client model is used
    client_model_cache_size: int = 20  # Max client models in LRU cache

    # --- Phase 1: Pipeline ---
    ocr_service_url: str = "http://ocr-service:8001"
    pipeline_timeout_seconds: int = 120
    pipeline_pages_per_batch: int = 5

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",")]

    @property
    def pg_dsn(self) -> str:
        return f"postgresql://{self.pg_user}:{self.pg_password}@{self.pg_host}:{self.pg_port}/{self.pg_database}"


settings = MlSettings()
