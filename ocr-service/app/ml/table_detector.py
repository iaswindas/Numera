"""PP-StructureV2 table detection wrapper.

Detects tables in document page images and converts them into structured
cell grids with row/column indices, header detection, and column-type
classification (ACCOUNT vs NUMERIC).
"""

import logging
import re
import uuid
from html.parser import HTMLParser

import numpy as np

from app.api.models import BoundingBox, DetectedTable, TableCell

logger = logging.getLogger("ocr-service.ml.table_detector")


class TableDetector:
    """Wrapper around PaddlePaddle PP-Structure for table detection."""

    def __init__(self, use_gpu: bool = False):
        self.is_loaded = False
        self.engine = None

        try:
            # PaddleX 3.x / PaddleOCR >= 2.8 import path
            from paddleocr import PPStructure

            self.engine = PPStructure(
                table=True,
                ocr=True,  # Need OCR for cell text content
                show_log=False,
                use_gpu=use_gpu,
            )
            self.is_loaded = True
            logger.info("PP-Structure engine loaded successfully")
        except ImportError:
            try:
                # Fallback: PaddleX pipeline API
                from paddlex import create_pipeline

                self.engine = create_pipeline("table_recognition")
                self.is_loaded = True
                logger.info("PaddleX table recognition pipeline loaded")
            except ImportError:
                logger.warning(
                    "Neither paddleocr.PPStructure nor paddlex available — "
                    "table detection disabled"
                )

    def detect_tables(
        self, image: np.ndarray, page_num: int
    ) -> list[DetectedTable]:
        """Detect and parse all tables on a page image."""
        if not self.is_loaded or self.engine is None:
            return []

        try:
            result = self.engine(image)
        except Exception:
            logger.exception("PP-Structure inference failed on page %d", page_num)
            return []

        tables: list[DetectedTable] = []
        table_idx = 0
        for item in result:
            if item.get("type") == "table":
                table = self._parse_table_result(
                    item, page_num, image.shape, table_idx
                )
                if table is not None:
                    tables.append(table)
                    table_idx += 1

        return tables

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _parse_table_result(
        self,
        item: dict,
        page_num: int,
        img_shape: tuple,
        table_idx: int,
    ) -> DetectedTable | None:
        """Convert PP-Structure output dict into a DetectedTable model."""
        h, w = img_shape[:2]

        # --- Bounding box ---
        bbox_coords = item.get("bbox", [0, 0, w, h])
        bbox = BoundingBox(
            x=bbox_coords[0] / w,
            y=bbox_coords[1] / h,
            width=(bbox_coords[2] - bbox_coords[0]) / w,
            height=(bbox_coords[3] - bbox_coords[1]) / h,
        )

        # --- Parse HTML table → cell grid ---
        html_str = ""
        res = item.get("res", {})
        if isinstance(res, dict):
            html_str = res.get("html", "")
        elif isinstance(res, str):
            html_str = res

        cells = self._parse_html_table(html_str, bbox_coords, img_shape)

        if not cells:
            return None  # Skip empty / false-positive tables

        # --- Derive row/col counts ---
        max_row = max(c.row_index + c.row_span for c in cells) if cells else 0
        max_col = max(c.col_index + c.col_span for c in cells) if cells else 0

        # --- Detect header rows (first rows that are all TEXT) ---
        header_rows = self._detect_header_rows(cells, max_col)

        # --- Detect column types ---
        account_col, value_cols = self._detect_column_types(cells, max_row, max_col, header_rows)

        table_id = f"p{page_num}_t{table_idx}_{uuid.uuid4().hex[:6]}"

        return DetectedTable(
            table_id=table_id,
            page_number=page_num,
            bbox=bbox,
            confidence=float(item.get("confidence", 0.9)),
            rows=max_row,
            cols=max_col,
            cells=cells,
            header_rows=header_rows,
            account_column=account_col,
            value_columns=value_cols,
        )

    def _parse_html_table(
        self,
        html: str,
        table_bbox: list,
        img_shape: tuple,
    ) -> list[TableCell]:
        """Parse the HTML table string from PP-Structure into TableCell list.

        PP-StructureV2 returns an HTML string like:
            <table><tr><td>Revenue</td><td>1,234</td></tr>...</table>

        We convert each <td>/<th> into a TableCell with approximate bounding
        boxes derived by distributing the table bounding box evenly. A more
        precise approach would use the cell-level coordinates when available.
        """
        parser = _HTMLTableParser()
        try:
            parser.feed(html)
        except Exception:
            logger.warning("Failed to parse HTML table: %s", html[:200])
            return []

        grid = parser.rows  # list[list[str]]
        if not grid:
            return []

        h, w = img_shape[:2]
        tx, ty, tx2, ty2 = table_bbox
        tw = tx2 - tx
        th = ty2 - ty

        num_rows = len(grid)
        num_cols = max(len(row) for row in grid) if grid else 0

        if num_rows == 0 or num_cols == 0:
            return []

        row_height = th / num_rows
        col_width = tw / num_cols

        cells: list[TableCell] = []
        for ri, row in enumerate(grid):
            for ci, cell_text in enumerate(row):
                # Approximate bounding box within the table
                cx = (tx + ci * col_width) / w
                cy = (ty + ri * row_height) / h
                cw = col_width / w
                ch = row_height / h

                cell_type = self._classify_cell(cell_text)
                is_header = ri == 0  # Simplified: first row is header

                cells.append(
                    TableCell(
                        text=cell_text.strip(),
                        bbox=BoundingBox(x=cx, y=cy, width=cw, height=ch),
                        row_index=ri,
                        col_index=ci,
                        row_span=1,
                        col_span=1,
                        is_header=is_header,
                        cell_type=cell_type,
                    )
                )

        return cells

    @staticmethod
    def _classify_cell(text: str) -> str:
        """Classify cell content as TEXT, NUMERIC, EMPTY, or MIXED."""
        text = text.strip()
        if not text or text in ("-", "—", "–", "−"):
            return "EMPTY"

        # Remove common formatting chars for numeric check
        cleaned = re.sub(r"[,.\s()%$€£¥₹]", "", text)
        if not cleaned:
            return "EMPTY"

        digit_ratio = sum(c.isdigit() for c in cleaned) / len(cleaned)
        alpha_ratio = sum(c.isalpha() for c in cleaned) / len(cleaned)

        if digit_ratio > 0.7:
            return "NUMERIC"
        if alpha_ratio > 0.7:
            return "TEXT"
        if digit_ratio > 0 and alpha_ratio > 0:
            return "MIXED"
        return "TEXT"

    @staticmethod
    def _detect_header_rows(
        cells: list[TableCell], num_cols: int
    ) -> list[int]:
        """Detect which rows are header rows (mostly non-numeric)."""
        if not cells:
            return []

        row_indices = sorted({c.row_index for c in cells})
        headers = []
        for ri in row_indices:
            row_cells = [c for c in cells if c.row_index == ri]
            non_numeric = sum(
                1 for c in row_cells if c.cell_type in ("TEXT", "EMPTY")
            )
            if non_numeric >= len(row_cells) * 0.7:
                headers.append(ri)
            else:
                break  # Stop at first data row
        return headers

    @staticmethod
    def _detect_column_types(
        cells: list[TableCell],
        num_rows: int,
        num_cols: int,
        header_rows: list[int],
    ) -> tuple[int | None, list[int]]:
        """Detect which column is the account label and which are value columns."""
        data_cells = [c for c in cells if c.row_index not in header_rows]
        if not data_cells:
            return None, []

        col_type_counts: dict[int, dict[str, int]] = {}
        for c in data_cells:
            col_type_counts.setdefault(c.col_index, {"TEXT": 0, "NUMERIC": 0, "OTHER": 0})
            if c.cell_type == "TEXT":
                col_type_counts[c.col_index]["TEXT"] += 1
            elif c.cell_type == "NUMERIC":
                col_type_counts[c.col_index]["NUMERIC"] += 1
            else:
                col_type_counts[c.col_index]["OTHER"] += 1

        account_column = None
        value_columns = []

        for ci in sorted(col_type_counts.keys()):
            counts = col_type_counts[ci]
            total = sum(counts.values()) or 1
            if counts["TEXT"] / total >= 0.7 and account_column is None:
                account_column = ci
            elif counts["NUMERIC"] / total >= 0.5:
                value_columns.append(ci)

        return account_column, value_columns


class _HTMLTableParser(HTMLParser):
    """Minimal HTML parser that extracts a grid from <table> markup."""

    def __init__(self):
        super().__init__()
        self.rows: list[list[str]] = []
        self._current_row: list[str] = []
        self._current_cell: list[str] = []
        self._in_cell = False

    def handle_starttag(self, tag: str, attrs):
        if tag == "tr":
            self._current_row = []
        elif tag in ("td", "th"):
            self._in_cell = True
            self._current_cell = []

    def handle_endtag(self, tag: str):
        if tag in ("td", "th"):
            self._in_cell = False
            self._current_row.append("".join(self._current_cell))
        elif tag == "tr":
            if self._current_row:
                self.rows.append(self._current_row)

    def handle_data(self, data: str):
        if self._in_cell:
            self._current_cell.append(data)
