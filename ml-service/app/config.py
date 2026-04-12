"""ML Service configuration via pydantic-settings."""

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class MlSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="ML_")

    host: str = "0.0.0.0"
    port: int = 8002
    debug: bool = False

    # Internal API key (must match backend's configured key)
    api_key: str = ""

    # Storage
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = ""
    minio_secret_key: str = ""

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
    pg_password: str = ""
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
    backend_url: str = "http://backend:8080"
    pipeline_timeout_seconds: int = 120
    pipeline_pages_per_batch: int = 5

    # --- Phase 1: NG-MILP ---
    enable_ng_milp: bool = False
    ng_milp_tolerance: float = 0.005
    ng_milp_timeout_ms: int = 5000
    ng_milp_use_ortools: bool = True
    ng_milp_gnn_weights: str = ""
    ng_milp_max_candidates_per_cell: int = 15
    ng_milp_candidate_row_window: int = 16
    ng_milp_max_addends: int = 6

    # --- Phase 1: STGH matching ---
    fingerprint_similarity_threshold: float = 0.85

    # --- Phase 1: FSO Federated Learning ---
    fso_enabled: bool = False
    fso_shared_subspace_dim: int = 64
    fso_private_subspace_dim: int = 32
    fso_min_tenants: int = 2
    fso_max_rounds: int = 100
    fso_learning_rate: float = 1e-3
    fso_clip_norm: float = 1.0
    fso_privacy_epsilon: float = 1.0
    fso_orthogonality_tolerance: float = 1e-6

    @model_validator(mode="after")
    def validate_security(self):
        if not self.debug:
            if not self.api_key.strip():
                raise ValueError("ML_API_KEY must be configured")
            if not self.minio_access_key.strip() or not self.minio_secret_key.strip():
                raise ValueError("ML_MINIO_ACCESS_KEY and ML_MINIO_SECRET_KEY must be configured")
            if not self.pg_password.strip():
                raise ValueError("ML_PG_PASSWORD must be configured")
        return self

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",")]

    @property
    def pg_dsn(self) -> str:
        return f"postgresql://{self.pg_user}:{self.pg_password}@{self.pg_host}:{self.pg_port}/{self.pg_database}"


settings = MlSettings()
