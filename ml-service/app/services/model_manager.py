"""MLflow model loader with staging support and HuggingFace fallback.

Supports loading both Production and Staging model versions from MLflow
for A/B testing. Falls back to HuggingFace Hub pre-trained models for
development when MLflow doesn't have models yet.
"""

import logging
from pathlib import Path
from typing import Optional

logger = logging.getLogger("ml-service.services.model_manager")


class ModelManager:
    """Loads models from MLflow registry, caches locally."""

    def __init__(self, mlflow_uri: str, local_cache_dir: str = "/app/models"):
        self.mlflow_uri = mlflow_uri
        self.cache_dir = Path(local_cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self._loaded_models: dict[str, Path] = {}
        self._model_info: dict[str, dict] = {}
        self.device = "cpu"

        try:
            import mlflow
            mlflow.set_tracking_uri(mlflow_uri)
            # Quick connectivity check — fail fast if MLflow is unreachable
            import urllib.request
            req = urllib.request.Request(f"{mlflow_uri}/api/2.0/mlflow/experiments/list?max_results=1")
            urllib.request.urlopen(req, timeout=3)
            self._mlflow_available = True
            logger.info("MLflow client connected to %s", mlflow_uri)
        except Exception as exc:
            self._mlflow_available = False
            logger.warning("MLflow not available (%s) — will use fallback models", exc)

    def load_model(self, model_name: str, stage: str = "Production") -> Path:
        """Download model from MLflow if not cached, return local path.

        Args:
            model_name: Registered model name in MLflow.
            stage: Model stage ('Production', 'Staging', 'latest').

        Returns:
            Path to the local model directory.

        Raises:
            FileNotFoundError: If model can't be loaded from any source.
        """
        cache_key = f"{model_name}/{stage}"
        if cache_key in self._loaded_models:
            return self._loaded_models[cache_key]

        # Try loading from MLflow
        if self._mlflow_available:
            try:
                import mlflow

                model_uri = f"models:/{model_name}/{stage}"
                logger.info("Downloading model %s from MLflow …", model_uri)
                local_path = mlflow.artifacts.download_artifacts(
                    artifact_uri=model_uri,
                    dst_path=str(self.cache_dir / model_name / stage),
                )
                resolved = Path(local_path)
                self._loaded_models[cache_key] = resolved
                self._model_info[cache_key] = {
                    "name": model_name,
                    "stage": stage,
                    "source": "mlflow",
                    "path": str(resolved),
                }
                logger.info("Model loaded from MLflow: %s → %s", model_uri, resolved)
                return resolved
            except Exception as exc:
                logger.warning("MLflow load failed for %s/%s: %s", model_name, stage, exc)

        # Fallback: local models/ directory
        fallback = self.cache_dir / model_name
        if fallback.exists():
            logger.info("Using local fallback model: %s", fallback)
            self._loaded_models[cache_key] = fallback
            self._model_info[cache_key] = {
                "name": model_name,
                "stage": stage,
                "source": "local_cache",
                "path": str(fallback),
            }
            return fallback

        raise FileNotFoundError(
            f"Model '{model_name}' not found in MLflow ({self.mlflow_uri}) "
            f"or local cache ({fallback})"
        )

    def load_staging_model(self, model_name: str) -> Optional[Path]:
        """Try to load a Staging model. Returns None if no Staging version exists.

        Used by A/B testing to load an alternative model version.
        """
        try:
            return self.load_model(model_name, stage="Staging")
        except (FileNotFoundError, Exception) as exc:
            logger.info("No Staging model for %s: %s", model_name, exc)
            return None

    def load_huggingface_model(self, hf_model_id: str) -> str:
        """Return a HuggingFace model ID for direct loading.

        Used as a fallback when MLflow doesn't have a model yet.
        """
        logger.info("Using HuggingFace model: %s", hf_model_id)
        return hf_model_id

    def get_model_info(self, model_name: str, stage: str = "Production") -> dict:
        """Get metadata about a loaded model."""
        cache_key = f"{model_name}/{stage}"
        return self._model_info.get(cache_key, {
            "name": model_name,
            "stage": stage,
            "source": "not_loaded",
        })

    def get_all_model_info(self) -> dict:
        """Get info about all loaded models."""
        return dict(self._model_info)
