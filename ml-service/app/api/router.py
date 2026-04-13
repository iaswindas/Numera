"""ML Service API router — mounts all sub-routers."""

from fastapi import APIRouter

from .health import router as health_router
from .zones import router as zones_router
from .mapping import router as mapping_router
from .feedback import router as feedback_router
from .pipeline import router as pipeline_router
from .expressions import router as expressions_router
from .fingerprint import router as fingerprint_router
from .covenant_prediction import router as covenant_prediction_router
from .anomaly_detection import router as anomaly_detection_router
from .rsbsn_prediction import router as rsbsn_prediction_router
from .knowledge_graph import router as knowledge_graph_router
from .federated import router as federated_router
from .copilot import router as copilot_router

router = APIRouter()

router.include_router(health_router, prefix="/ml", tags=["health"])
router.include_router(zones_router, prefix="/ml/zones", tags=["zones"])
router.include_router(mapping_router, prefix="/ml/mapping", tags=["mapping"])
router.include_router(feedback_router, prefix="/ml", tags=["feedback"])
router.include_router(pipeline_router, prefix="/ml/pipeline", tags=["pipeline"])
router.include_router(expressions_router, prefix="/ml/expressions", tags=["expressions"])
router.include_router(fingerprint_router, prefix="/ml/fingerprint", tags=["fingerprint"])
router.include_router(covenant_prediction_router, prefix="/ml", tags=["covenant"])
router.include_router(rsbsn_prediction_router, prefix="/ml", tags=["covenant-rsbsn"])
router.include_router(anomaly_detection_router, prefix="/ml", tags=["anomaly"])
router.include_router(knowledge_graph_router, prefix="/ml", tags=["knowledge-graph"])
router.include_router(federated_router, prefix="/ml", tags=["federated"])
router.include_router(copilot_router, prefix="/ml", tags=["copilot"])

