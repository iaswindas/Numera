"""Per-tenant model resolution for client-specific Sentence-BERT models.

When a tenant accumulates enough corrections (configurable threshold),
a client-specific SBERT model is fine-tuned on Colab and registered in
MLflow as 'sbert-ifrs-matcher-{tenant_id}'. This resolver loads and
caches those client models.
"""

import logging
from collections import OrderedDict
from typing import Optional

logger = logging.getLogger("ml-service.services.client_model_resolver")


class ClientModelResolver:
    """Tenant-aware model resolution with LRU caching.

    Checks MLflow for a tenant-specific model, falls back to global.
    Models are lazy-loaded on first request per tenant and cached in
    an LRU cache to bound memory usage.
    """

    def __init__(self, model_manager, settings):
        self._model_manager = model_manager
        self._min_corrections = settings.client_model_min_corrections
        self._enabled = settings.enable_client_models
        self._max_cache = settings.client_model_cache_size
        self._cache: OrderedDict[str, object] = OrderedDict()  # LRU cache

    async def resolve(self, tenant_id: str, app_state) -> Optional[object]:
        """Resolve a client-specific SemanticMatcher for the given tenant.

        Returns:
            A tenant-specific SemanticMatcher, or None to use the global one.
        """
        if not self._enabled or not tenant_id:
            return None

        # Check LRU cache
        if tenant_id in self._cache:
            self._cache.move_to_end(tenant_id)
            logger.debug("Client model cache hit: tenant=%s", tenant_id)
            return self._cache[tenant_id]

        # Check if tenant has enough corrections to warrant a client model
        feedback_store = getattr(app_state, "feedback_store", None)
        if feedback_store:
            count = await feedback_store.count_by_tenant(tenant_id)
            if count < self._min_corrections:
                logger.debug(
                    "Tenant %s has %d corrections (need %d) — using global model",
                    tenant_id, count, self._min_corrections,
                )
                return None

        # Try loading tenant-specific model from MLflow
        model_name = f"sbert-ifrs-matcher-{tenant_id}"
        try:
            model_path = self._model_manager.load_model(model_name, stage="Production")
            from sentence_transformers import SentenceTransformer
            from app.ml.semantic_matcher import SemanticMatcher
            from app.config import settings

            # Create a matcher with the client model
            client_matcher = SemanticMatcher.__new__(SemanticMatcher)
            client_matcher.is_loaded = True
            client_matcher.model = SentenceTransformer(str(model_path))
            client_matcher._staging_model = None
            client_matcher._staging_loaded = False
            client_matcher._target_cache = {}
            client_matcher._taxonomy = {}
            client_matcher._high_threshold = settings.mapping_high_confidence
            client_matcher._medium_threshold = settings.mapping_medium_confidence
            client_matcher._ab_ratio = 0.0
            client_matcher._ab_enabled = False

            # Add to LRU cache
            self._cache[tenant_id] = client_matcher
            if len(self._cache) > self._max_cache:
                evicted_key, _ = self._cache.popitem(last=False)
                logger.info("Evicted client model from cache: tenant=%s", evicted_key)

            logger.info("Loaded client model: tenant=%s model=%s", tenant_id, model_name)
            return client_matcher

        except FileNotFoundError:
            logger.debug("No client model found for tenant=%s", tenant_id)
            return None
        except Exception:
            logger.exception("Failed to load client model for tenant=%s", tenant_id)
            return None
