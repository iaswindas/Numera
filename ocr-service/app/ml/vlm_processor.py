"""Qwen3-VL-8B Vision Language Model processor.

Replaces the 3-model pipeline (PaddleOCR + PP-Structure + LayoutLM) with
a single VLM forward pass that handles OCR, table detection, table structure
parsing, and zone classification in one shot.

Supports:
- Full precision (float16) for GPU inference
- 4-bit quantized (bitsandbytes) for CPU/small GPU
- Batched multi-page processing
"""

import json
import logging
import re
import uuid
from pathlib import Path
from typing import Optional

import numpy as np
from PIL import Image

logger = logging.getLogger("ocr-service.ml.vlm_processor")


class Qwen3VLProcessor:
    """Qwen3-VL-8B based document processor.

    Handles OCR, table extraction, zone classification, and layout
    analysis in a single forward pass per page.
    """

    def __init__(
        self,
        model_id: str = "Qwen/Qwen3-VL-8B-Instruct",
        device: str = "auto",
        quantize: bool = True,
        model_path: Optional[str] = None,
        max_new_tokens: int = 4096,
    ):
        """Initialise the Qwen3-VL processor.

        Args:
            model_id: HuggingFace model ID or local path.
            device: Device to load model on ("auto", "cpu", "cuda").
            quantize: Use 4-bit quantization (recommended for < 24GB VRAM).
            model_path: Override path for fine-tuned model (from MLflow).
            max_new_tokens: Max tokens to generate per inference.
        """
        self.model_id = model_path or model_id
        self.device = device
        self.quantize = quantize
        self.max_new_tokens = max_new_tokens
        self.is_loaded = False
        self.model = None
        self.processor = None

        self._load_model()

    def _load_model(self):
        """Load the Qwen3-VL model and processor."""
        try:
            from transformers import Qwen2_5_VLForConditionalGeneration, AutoProcessor

            load_kwargs = {
                "trust_remote_code": False,
                "device_map": self.device,
            }

            if self.quantize:
                try:
                    from transformers import BitsAndBytesConfig
                    import torch

                    load_kwargs["quantization_config"] = BitsAndBytesConfig(
                        load_in_4bit=True,
                        bnb_4bit_compute_dtype=torch.float16,
                        bnb_4bit_use_double_quant=True,
                        bnb_4bit_quant_type="nf4",
                    )
                    logger.info("Using 4-bit quantization")
                except ImportError:
                    logger.warning("bitsandbytes not available — loading in float16")
                    import torch
                    load_kwargs["torch_dtype"] = torch.float16
            else:
                import torch
                load_kwargs["torch_dtype"] = torch.float16

            logger.info("Loading Qwen3-VL from %s ...", self.model_id)

            # Qwen3-VL uses the Qwen2.5-VL architecture class
            self.model = Qwen2_5_VLForConditionalGeneration.from_pretrained(
                self.model_id, **load_kwargs
            )
            self.processor = AutoProcessor.from_pretrained(
                self.model_id, trust_remote_code=False
            )

            self.is_loaded = True
            logger.info("Qwen3-VL loaded successfully (quantized=%s)", self.quantize)

        except Exception:
            logger.exception("Failed to load Qwen3-VL model")
            self.is_loaded = False

    def extract_page(
        self,
        image: Image.Image | np.ndarray,
        page_number: int,
        prompt: str | None = None,
    ) -> dict:
        """Extract all tables and text from a single page image.

        Args:
            image: PIL Image or numpy array of the page.
            page_number: Page number (1-indexed).
            prompt: Custom prompt, or uses default PAGE_EXTRACTION_PROMPT.

        Returns:
            Dict with 'tables', 'page_text', 'page_type' keys.
        """
        if not self.is_loaded:
            logger.warning("Qwen3-VL not loaded — returning empty result")
            return {"tables": [], "page_text": "", "page_type": "unknown"}

        from app.ml.vlm_prompts import SYSTEM_PROMPT, PAGE_EXTRACTION_PROMPT

        if isinstance(image, np.ndarray):
            image = Image.fromarray(image)

        user_prompt = prompt or PAGE_EXTRACTION_PROMPT

        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image},
                    {"type": "text", "text": user_prompt},
                ],
            },
        ]

        raw_text = self._generate(messages)
        result = self._parse_json_response(raw_text)

        # Enrich tables with page number and IDs
        for i, table in enumerate(result.get("tables", [])):
            table["table_id"] = table.get("table_id", f"p{page_number}_t{i+1}")
            table["page_number"] = page_number

        return result

    def classify_zone(self, image: Image.Image | np.ndarray) -> dict:
        """Classify what type of financial statement is shown.

        Args:
            image: Image of the table region.

        Returns:
            Dict with 'zone_type', 'zone_label', 'confidence'.
        """
        if not self.is_loaded:
            return {"zone_type": "OTHER", "zone_label": "Unknown", "confidence": 0.0}

        from app.ml.vlm_prompts import SYSTEM_PROMPT, ZONE_CLASSIFICATION_PROMPT

        if isinstance(image, np.ndarray):
            image = Image.fromarray(image)

        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image},
                    {"type": "text", "text": ZONE_CLASSIFICATION_PROMPT},
                ],
            },
        ]

        raw_text = self._generate(messages)
        return self._parse_json_response(raw_text, fallback={
            "zone_type": "OTHER", "zone_label": "Unknown", "confidence": 0.0,
        })

    def extract_text(self, image: Image.Image | np.ndarray) -> str:
        """OCR-only: extract all text from a page image.

        Args:
            image: Page image.

        Returns:
            Extracted text string.
        """
        if not self.is_loaded:
            return ""

        from app.ml.vlm_prompts import OCR_EXTRACTION_PROMPT

        if isinstance(image, np.ndarray):
            image = Image.fromarray(image)

        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image},
                    {"type": "text", "text": OCR_EXTRACTION_PROMPT},
                ],
            },
        ]

        return self._generate(messages)

    def _generate(self, messages: list[dict]) -> str:
        """Run inference on the VLM.

        Args:
            messages: Chat-format messages with text and image content.

        Returns:
            Generated text string.
        """
        import torch

        text_input = self.processor.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True
        )

        # Process images from messages
        images = []
        for msg in messages:
            content = msg.get("content", [])
            if isinstance(content, list):
                for item in content:
                    if isinstance(item, dict) and item.get("type") == "image":
                        images.append(item["image"])

        if images:
            inputs = self.processor(
                text=[text_input],
                images=images,
                padding=True,
                return_tensors="pt",
            )
        else:
            inputs = self.processor(
                text=[text_input],
                padding=True,
                return_tensors="pt",
            )

        inputs = inputs.to(self.model.device)

        with torch.no_grad():
            output_ids = self.model.generate(
                **inputs,
                max_new_tokens=self.max_new_tokens,
                do_sample=False,
                temperature=None,
                top_p=None,
            )

        # Decode only the generated tokens (skip input)
        generated_ids = output_ids[0][inputs["input_ids"].shape[1]:]
        response = self.processor.decode(generated_ids, skip_special_tokens=True)
        return response.strip()

    @staticmethod
    def _parse_json_response(text: str, fallback: dict | None = None) -> dict:
        """Parse JSON from VLM response, handling markdown fences."""
        # Strip markdown code fences if present
        text = text.strip()
        if text.startswith("```"):
            text = re.sub(r"^```(?:json)?\s*\n?", "", text)
            text = re.sub(r"\n?```\s*$", "", text)

        try:
            return json.loads(text)
        except json.JSONDecodeError:
            logger.warning("Failed to parse VLM JSON response: %s...", text[:200])
            if fallback is not None:
                return fallback
            return {"tables": [], "page_text": text, "page_type": "parse_error"}

    def build_detected_tables(self, vlm_result: dict, page_number: int) -> list:
        """Convert VLM extraction result to DetectedTable format.

        Bridges the VLM output to the existing API models so downstream
        services (zone classification, mapping) work unchanged.
        """
        from app.api.models import BoundingBox, DetectedTable, TableCell

        tables = []
        for t in vlm_result.get("tables", []):
            rows_data = t.get("rows", [])
            headers = t.get("headers", [])
            num_cols = max(len(headers), max((len(r.get("values", [])) for r in rows_data), default=0) + 1)

            cells: list[TableCell] = []

            # Header row cells
            for col_idx, header_text in enumerate(headers):
                cells.append(TableCell(
                    text=str(header_text),
                    bbox=BoundingBox(x=col_idx/num_cols, y=0, width=1/num_cols, height=0.05),
                    row_index=0,
                    col_index=col_idx,
                    is_header=True,
                    cell_type="TEXT",
                ))

            # Data rows
            for row_idx, row in enumerate(rows_data, start=1):
                label = row.get("label", "")
                cells.append(TableCell(
                    text=label,
                    bbox=BoundingBox(x=0, y=row_idx*0.05, width=1/num_cols, height=0.05),
                    row_index=row_idx,
                    col_index=0,
                    is_header=row.get("is_header", False),
                    cell_type="TEXT",
                ))

                for val_idx, val in enumerate(row.get("values", []), start=1):
                    val_str = str(val) if val is not None else ""
                    cell_type = "NUMERIC" if val is not None else "EMPTY"
                    cells.append(TableCell(
                        text=val_str,
                        bbox=BoundingBox(
                            x=val_idx/num_cols, y=row_idx*0.05,
                            width=1/num_cols, height=0.05,
                        ),
                        row_index=row_idx,
                        col_index=val_idx,
                        is_header=False,
                        cell_type=cell_type,
                    ))

            # Build bounding box from VLM output or use defaults
            raw_bbox = t.get("bbox", [0, 0, 100, 100])
            bbox = BoundingBox(
                x=raw_bbox[0] / 100, y=raw_bbox[1] / 100,
                width=(raw_bbox[2] - raw_bbox[0]) / 100,
                height=(raw_bbox[3] - raw_bbox[1]) / 100,
            )

            table = DetectedTable(
                table_id=t.get("table_id", f"p{page_number}_t{uuid.uuid4().hex[:6]}"),
                page_number=page_number,
                bbox=bbox,
                confidence=float(t.get("confidence", 0.9)),
                rows=len(rows_data) + 1,  # +1 for header
                cols=num_cols,
                cells=cells,
                header_rows=[0],
                account_column=0,
                value_columns=list(range(1, num_cols)),
                detected_periods=t.get("periods", []),
                detected_currency=t.get("currency"),
                detected_unit=t.get("unit"),
                # VLM zone pre-classification (comes free with extraction)
                vlm_zone_type=t.get("zone_type"),
                vlm_zone_label=t.get("zone_label"),
                vlm_zone_confidence=float(t.get("zone_confidence", 0.0))
                    if t.get("zone_confidence") else None,
            )
            tables.append(table)

        return tables
