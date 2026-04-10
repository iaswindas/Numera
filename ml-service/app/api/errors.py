"""Structured ML error models and pre-defined error codes.

Used across the ML service for consistent, structured error responses
that support graceful degradation.
"""

from app.api.models import MLError


# --- Pre-defined error factories ---

def zone_classification_failure(message: str, page: int | None = None) -> MLError:
    return MLError(
        error_code="ZONE_CLASSIFICATION_FAILURE",
        message=message,
        page=page,
        recoverable=True,
        detail="Zone defaults to OTHER with low confidence",
    )


def mapping_failure(message: str) -> MLError:
    return MLError(
        error_code="MAPPING_FAILURE",
        message=message,
        recoverable=True,
        detail="Mapping returns empty suggestions",
    )


def model_unavailable(model_name: str) -> MLError:
    return MLError(
        error_code="MODEL_UNAVAILABLE",
        message=f"Model '{model_name}' is not loaded — using fallback",
        recoverable=True,
    )


def pipeline_step_failure(step: str, message: str) -> MLError:
    return MLError(
        error_code=f"PIPELINE_{step.upper()}_FAILURE",
        message=message,
        recoverable=True,
    )


def pipeline_timeout(step: str, timeout_seconds: int) -> MLError:
    return MLError(
        error_code="PIPELINE_TIMEOUT",
        message=f"Step '{step}' timed out after {timeout_seconds}s",
        recoverable=False,
    )
