"""Image preprocessing pipeline for OCR robustness."""

from __future__ import annotations

from dataclasses import dataclass
import logging

import numpy as np

try:
    import cv2
except Exception:  # pragma: no cover - optional dependency in lightweight envs
    cv2 = None

logger = logging.getLogger("ocr-service.image_preprocessor")


@dataclass(slots=True)
class PreprocessOptions:
    enable_despeckle_low: bool = True
    enable_despeckle_high: bool = True
    enable_deskew: bool = True
    enable_watermark_removal: bool = True
    max_deskew_angle_degrees: float = 15.0


def preprocess_image(image: np.ndarray, options: PreprocessOptions) -> np.ndarray:
    """Apply configurable OCR preprocessing operations.

    Steps are intentionally conservative to preserve financial table text.
    """
    if image is None or image.size == 0:
        return image

    if cv2 is None:
        logger.debug("OpenCV unavailable; skipping preprocessing steps")
        return image

    result = image.copy()

    if options.enable_despeckle_low:
        result = _despeckle_low(result)
    if options.enable_despeckle_high:
        result = _despeckle_high(result)
    if options.enable_watermark_removal:
        result = _suppress_watermark(result)
    if options.enable_deskew:
        result = _deskew(result, options.max_deskew_angle_degrees)

    return result


def _despeckle_low(image: np.ndarray) -> np.ndarray:
    return cv2.medianBlur(image, 3)


def _despeckle_high(image: np.ndarray) -> np.ndarray:
    if image.ndim == 2:
        return cv2.fastNlMeansDenoising(image, h=10, templateWindowSize=7, searchWindowSize=21)
    return cv2.fastNlMeansDenoisingColored(image, None, 10, 10, 7, 21)


def _suppress_watermark(image: np.ndarray) -> np.ndarray:
    gray = _to_gray(image)
    # Light, low-contrast overlays tend to be high-intensity sparse regions.
    mask = cv2.inRange(gray, 220, 255)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8), iterations=1)
    if cv2.countNonZero(mask) == 0:
        return image

    if image.ndim == 2:
        inpainted = cv2.inpaint(image, mask, 3, cv2.INPAINT_TELEA)
    else:
        inpainted = cv2.inpaint(image, mask, 3, cv2.INPAINT_TELEA)

    return inpainted


def _deskew(image: np.ndarray, max_abs_angle: float) -> np.ndarray:
    gray = _to_gray(image)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    _, bw = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)

    coords = np.column_stack(np.where(bw > 0))
    if coords.shape[0] < 20:
        return image

    rect = cv2.minAreaRect(coords)
    angle = rect[-1]
    if angle < -45:
        angle = 90 + angle
    angle = float(max(min(angle, max_abs_angle), -max_abs_angle))

    if abs(angle) < 0.2:
        return image

    h, w = image.shape[:2]
    center = (w // 2, h // 2)
    matrix = cv2.getRotationMatrix2D(center, angle, 1.0)

    border = 255 if image.ndim == 2 else (255, 255, 255)
    return cv2.warpAffine(
        image,
        matrix,
        (w, h),
        flags=cv2.INTER_CUBIC,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=border,
    )


def _to_gray(image: np.ndarray) -> np.ndarray:
    if image.ndim == 2:
        return image
    return cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
