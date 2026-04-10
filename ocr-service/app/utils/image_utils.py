"""Image preprocessing utilities for scanned document OCR.

Provides deskew detection, contrast enhancement (CLAHE), and noise reduction
to improve OCR accuracy on low-quality scans.
"""

import logging

import numpy as np

logger = logging.getLogger("ocr-service.utils.image_utils")


def deskew(image: np.ndarray, max_angle: float = 10.0) -> np.ndarray:
    """Detect and correct skew in a scanned document image.

    Uses Hough line detection to find the dominant text angle and
    rotates the image to correct it. Only corrects angles within
    ±max_angle degrees.

    Args:
        image: BGR or grayscale image as numpy array.
        max_angle: Maximum skew angle to correct (degrees).

    Returns:
        Deskewed image.
    """
    try:
        import cv2
    except ImportError:
        logger.warning("cv2 not available — skipping deskew")
        return image

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if len(image.shape) == 3 else image
    edges = cv2.Canny(gray, 50, 150, apertureSize=3)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 180, 100, minLineLength=100, maxLineGap=10)

    if lines is None:
        return image

    angles = []
    for line in lines:
        x1, y1, x2, y2 = line[0]
        angle = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        if abs(angle) < max_angle:
            angles.append(angle)

    if not angles:
        return image

    median_angle = np.median(angles)
    if abs(median_angle) < 0.5:  # Less than 0.5° — not worth correcting
        return image

    h, w = image.shape[:2]
    center = (w // 2, h // 2)
    matrix = cv2.getRotationMatrix2D(center, median_angle, 1.0)
    rotated = cv2.warpAffine(
        image, matrix, (w, h),
        flags=cv2.INTER_CUBIC,
        borderMode=cv2.BORDER_REPLICATE,
    )
    logger.debug("Deskewed image by %.2f°", median_angle)
    return rotated


def enhance_contrast(image: np.ndarray, clip_limit: float = 2.0, tile_size: int = 8) -> np.ndarray:
    """Apply CLAHE (Contrast Limited Adaptive Histogram Equalization).

    Improves OCR accuracy on low-contrast scanned documents by enhancing
    local contrast without over-amplifying noise.

    Args:
        image: BGR image as numpy array.
        clip_limit: CLAHE clip limit (higher = more contrast).
        tile_size: Grid size for local histogram equalization.

    Returns:
        Contrast-enhanced image.
    """
    try:
        import cv2
    except ImportError:
        logger.warning("cv2 not available — skipping contrast enhancement")
        return image

    if len(image.shape) == 2:
        # Grayscale
        clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(tile_size, tile_size))
        return clahe.apply(image)

    # Convert to LAB colour space, enhance L channel
    lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=clip_limit, tileGridSize=(tile_size, tile_size))
    l = clahe.apply(l)
    lab = cv2.merge((l, a, b))
    enhanced = cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)
    return enhanced


def denoise(image: np.ndarray, strength: int = 10) -> np.ndarray:
    """Apply non-local means denoising.

    Reduces noise in scanned documents while preserving text edges.

    Args:
        image: BGR image as numpy array.
        strength: Filter strength (higher = more aggressive denoising).

    Returns:
        Denoised image.
    """
    try:
        import cv2
    except ImportError:
        logger.warning("cv2 not available — skipping denoising")
        return image

    if len(image.shape) == 2:
        return cv2.fastNlMeansDenoising(image, None, strength, 7, 21)
    return cv2.fastNlMeansDenoisingColored(image, None, strength, strength, 7, 21)
