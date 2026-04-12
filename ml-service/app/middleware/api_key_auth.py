"""API key authentication middleware for internal services.

Validates the X-API-Key header against a configured secret to ensure
only authorized services (the Kotlin backend) can call ML endpoints.
"""

import logging
import hmac
from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

logger = logging.getLogger("ml-service.middleware.auth")

# Paths that don't require authentication
PUBLIC_PATHS = frozenset({"/api/ml/health", "/api/ocr/health", "/docs", "/openapi.json", "/redoc"})


class ApiKeyMiddleware(BaseHTTPMiddleware):
    """Validates X-API-Key header on non-health endpoints."""

    def __init__(self, app, api_key: str):
        super().__init__(app)
        self.api_key = api_key

    async def dispatch(self, request: Request, call_next):
        path = request.url.path

        # Allow health checks and docs without auth
        if path in PUBLIC_PATHS or path.startswith("/docs") or path.startswith("/redoc"):
            return await call_next(request)

        # Validate API key
        provided_key = request.headers.get("X-API-Key")
        if not provided_key or not hmac.compare_digest(provided_key, self.api_key):
            logger.warning("Unauthorized access attempt to %s from %s", path, request.client.host if request.client else "unknown")
            return JSONResponse(
                status_code=401,
                content={"error": "unauthorized", "detail": "Invalid or missing API key"},
            )

        return await call_next(request)
