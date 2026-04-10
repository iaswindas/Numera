"""Test fixtures for ML service tests."""

import pytest
from fastapi.testclient import TestClient


@pytest.fixture
def app():
    """Create a test FastAPI application."""
    from app.main import app
    return app


@pytest.fixture
def client(app):
    """Create a test HTTP client."""
    return TestClient(app)
