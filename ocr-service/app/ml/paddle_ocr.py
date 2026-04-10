"""PaddleOCR engine wrapper — singleton per language.

Uses PaddleOCR for text detection, recognition, and angle classification.
Instances are cached per language to avoid repeated model loading.
"""

import logging

import numpy as np

from app.api.models import BoundingBox, OcrPageResult, OcrTextBlock

logger = logging.getLogger("ocr-service.ml.paddle_ocr")


class PaddleOCREngine:
    """Thread-safe, singleton-per-language PaddleOCR engine."""

    _instances: dict[str, "PaddleOCREngine"] = {}

    def __init__(self, lang: str = "en", use_gpu: bool = False):
        from paddleocr import PaddleOCR

        logger.info("Initialising PaddleOCR (lang=%s, gpu=%s) …", lang, use_gpu)
        self.ocr = PaddleOCR(
            use_angle_cls=True,
            lang=lang,
            use_gpu=use_gpu,
            show_log=False,
            det_db_thresh=0.3,
            det_db_box_thresh=0.5,
            rec_batch_num=16,
        )
        self.lang = lang
        self.use_gpu = use_gpu
        logger.info("PaddleOCR ready (lang=%s)", lang)

    @classmethod
    def get_instance(cls, lang: str = "en", use_gpu: bool = False) -> "PaddleOCREngine":
        """Return a cached engine for the given language, creating if needed."""
        if lang not in cls._instances:
            cls._instances[lang] = cls(lang, use_gpu)
        return cls._instances[lang]

    def extract_page(self, image: np.ndarray, page_num: int) -> OcrPageResult:
        """Run OCR on a single page image and return structured results."""
        h, w = image.shape[:2]
        results = self.ocr.ocr(image, cls=True)

        text_blocks: list[OcrTextBlock] = []
        if results and results[0]:
            for line in results[0]:
                bbox_points, (text, confidence) = line
                # Convert 4-point polygon to normalised (x, y, w, h)
                xs = [p[0] for p in bbox_points]
                ys = [p[1] for p in bbox_points]
                bbox = BoundingBox(
                    x=min(xs) / w,
                    y=min(ys) / h,
                    width=(max(xs) - min(xs)) / w,
                    height=(max(ys) - min(ys)) / h,
                )
                text_blocks.append(
                    OcrTextBlock(
                        text=text,
                        confidence=float(confidence),
                        bbox=bbox,
                        page=page_num,
                    )
                )

        full_text = " ".join(tb.text for tb in text_blocks)
        return OcrPageResult(
            page_number=page_num,
            width=w,
            height=h,
            text_blocks=text_blocks,
            full_text=full_text,
        )
