"""OW-PGGR: Outlier-Weighted Probabilistic Graph-Guided Reasoner.

Detects financial statement anomalies using statistical outlier detection,
graph-based consistency checking, materiality weighting, and cross-period
trend analysis.
"""

from app.ml.owpggr.detector import AnomalyDetector
from app.ml.owpggr.materiality import MaterialityCalculator
from app.ml.owpggr.models import Anomaly, AnomalyReport, AnomalyType

__all__ = [
    "AnomalyDetector",
    "AnomalyReport",
    "AnomalyType",
    "Anomaly",
    "MaterialityCalculator",
]
