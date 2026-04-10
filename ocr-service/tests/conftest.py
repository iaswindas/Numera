"""Test fixtures for OCR service tests."""

import pytest
import numpy as np
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


@pytest.fixture
def sample_image():
    """Generate a simple test image (white page with black text area)."""
    img = np.ones((2480, 3508, 3), dtype=np.uint8) * 255  # A4 at 300 DPI
    # Add a dark rectangle to simulate text area
    img[200:400, 300:3200] = 50
    return img


@pytest.fixture
def sample_pdf_bytes():
    """Generate minimal valid PDF bytes for testing."""
    try:
        import fitz
        doc = fitz.open()
        page = doc.new_page(width=595, height=842)  # A4
        page.insert_text((72, 72), "Revenue\n1,234,567\nCost of Sales\n(987,654)")
        pdf_bytes = doc.write()
        doc.close()
        return pdf_bytes
    except ImportError:
        return b"%PDF-1.4 minimal"
