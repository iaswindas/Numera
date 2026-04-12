"""Expression engine for financial statement mapping.

Detects and builds mapping expressions from extracted PDF data to model
template line items. Handles:
- Direct 1:1 mapping
- Sum expressions (Revenue = Product Sales + Service Income)
- Sign flips (Finance Costs shown as positive → mapped as negative)
- Unit conversions (thousands → actual)
- Total row detection (map to total instead of children)

This is the core intelligence that distinguishes Numera from simple OCR.
"""

import logging
import re
from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum
from typing import Optional

from app.config import settings

logger = logging.getLogger("ml-service.ml.expression_engine")


# ─── Expression Types ──────────────────────────────────────────


class ExpressionType(str, Enum):
    """Types of mapping expressions."""
    DIRECT = "DIRECT"           # Cell = source_value
    SUM = "SUM"                 # Cell = source_1 + source_2 + ...
    NEGATE = "NEGATE"           # Cell = -source_value
    ABSOLUTE = "ABSOLUTE"       # Cell = |source_value|
    SCALE = "SCALE"             # Cell = source_value × factor
    MANUAL = "MANUAL"           # Cell = analyst keyboard entry
    FORMULA = "FORMULA"         # Cell = computed from other model cells


@dataclass
class SourceRef:
    """A reference to a value extracted from the PDF."""
    row_index: int              # Index in the extracted rows
    label: str                  # e.g. "Product Sales"
    value: Optional[float] = None
    page: int = 0
    confidence: float = 0.0


@dataclass
class MappingExpression:
    """A complete mapping expression from PDF values to a model cell."""
    target_item_id: str         # Model template line item ID (e.g., "IS001")
    target_label: str           # e.g., "Revenue"
    expression_type: ExpressionType = ExpressionType.DIRECT
    sources: list[SourceRef] = field(default_factory=list)
    scale_factor: float = 1.0   # For unit conversion (e.g., 1000)
    computed_value: Optional[float] = None
    confidence: float = 0.0
    explanation: str = ""       # Human-readable explanation


@dataclass
class ExtractedRow:
    """A row from the VLM/OCR extraction."""
    index: int
    label: str
    values: list[float | None]
    is_total: bool = False
    is_header: bool = False
    indent_level: int = 0
    page: int = 0


# ─── Expression Engine ────────────────────────────────────────


class ExpressionEngine:
    """Builds mapping expressions from extracted PDF rows to model items.

    The engine uses a 3-pass algorithm:
    1. Match labels semantically (done by SemanticMatcher)
    2. Detect parent-child relationships via indentation
    3. Build optimal expressions (direct vs sum vs cross-reference)
    """

    def __init__(self, tolerance: float = 0.01):
        """Init.

        Args:
            tolerance: Relative tolerance for arithmetic validation.
                       0.01 = 1% difference allowed (for rounding).
        """
        self.tolerance = tolerance
        self._milp_solver = None

        if settings.enable_ng_milp:
            from app.ml.ng_milp import NGMILPConfig, NGMILPSolver

            self._milp_solver = NGMILPSolver(
                NGMILPConfig(
                    tolerance=settings.ng_milp_tolerance,
                    solver_timeout_ms=settings.ng_milp_timeout_ms,
                    use_ortools=settings.ng_milp_use_ortools,
                    gnn_weights_path=settings.ng_milp_gnn_weights,
                    max_candidates_per_cell=settings.ng_milp_max_candidates_per_cell,
                    candidate_row_window=settings.ng_milp_candidate_row_window,
                    max_addends=settings.ng_milp_max_addends,
                )
            )
            self.tolerance = settings.ng_milp_tolerance

    def build_expressions(
        self,
        extracted_rows: list[ExtractedRow],
        semantic_matches: list[dict],
        model_items: list[dict],
        period_index: int = 0,
    ) -> list[MappingExpression]:
        """Build mapping expressions for all model items.

        Args:
            extracted_rows: Rows from VLM/OCR extraction.
            semantic_matches: List of {row_index, target_item_id, confidence} dicts.
            model_items: Model template line items (from ifrs_corporate.json).
            period_index: Which period column to use (0 = latest).

        Returns:
            List of MappingExpression for each mappable item.
        """
        expressions = []
        milp_expressions = self._discover_sum_expressions(extracted_rows, period_index)

        # Index extracted rows by their semantic match
        row_to_item = {}
        for match in semantic_matches:
            row_idx = match["row_index"]
            item_id = match["target_item_id"]
            confidence = match.get("confidence", 0.0)
            if row_idx not in row_to_item or confidence > row_to_item[row_idx][1]:
                row_to_item[row_idx] = (item_id, confidence)

        # Reverse map: item_id → [matched row indices]
        item_to_rows = {}
        for row_idx, (item_id, conf) in row_to_item.items():
            item_to_rows.setdefault(item_id, []).append((row_idx, conf))

        # Build parent-child tree from indentation
        parent_children = self._detect_hierarchy(extracted_rows)

        for model_item in model_items:
            item_id = model_item["id"]
            item_label = model_item["label"]
            item_type = model_item.get("type", "INPUT")

            if item_type in ("FORMULA", "CATEGORY", "VALIDATION"):
                continue  # Skip computed items

            matched_rows = item_to_rows.get(item_id, [])

            if not matched_rows:
                # No match found — skip
                continue

            # Sort by confidence descending
            matched_rows.sort(key=lambda x: x[1], reverse=True)
            best_row_idx, best_conf = matched_rows[0]
            best_row = extracted_rows[best_row_idx]

            # Get value for this period
            value = self._get_value(best_row, period_index)

            if best_row_idx in milp_expressions:
                expressions.append(
                    self._build_ng_milp_expression(
                        target_id=item_id,
                        target_label=item_label,
                        expression=milp_expressions[best_row_idx],
                        confidence=best_conf,
                    )
                )
                continue

            # Check if this row is a total with children
            children = parent_children.get(best_row_idx, [])
            sign_convention = model_item.get("sign", "NATURAL")

            if best_row.is_total and children:
                # Try SUM expression: total = sum of children
                expr = self._try_sum_expression(
                    target_id=item_id,
                    target_label=item_label,
                    total_row=best_row,
                    children_indices=children,
                    extracted_rows=extracted_rows,
                    period_index=period_index,
                    confidence=best_conf,
                )
                if expr:
                    expressions.append(expr)
                    continue

            # Check if value needs sign flip
            if sign_convention == "NEGATIVE" and value is not None and value > 0:
                expr = MappingExpression(
                    target_item_id=item_id,
                    target_label=item_label,
                    expression_type=ExpressionType.NEGATE,
                    sources=[SourceRef(
                        row_index=best_row_idx,
                        label=best_row.label,
                        value=value,
                        page=best_row.page,
                        confidence=best_conf,
                    )],
                    computed_value=-value if value else None,
                    confidence=best_conf,
                    explanation=f"{item_label} = NEG({best_row.label})",
                )
                expressions.append(expr)
                continue

            # Direct mapping (most common)
            expr = MappingExpression(
                target_item_id=item_id,
                target_label=item_label,
                expression_type=ExpressionType.DIRECT,
                sources=[SourceRef(
                    row_index=best_row_idx,
                    label=best_row.label,
                    value=value,
                    page=best_row.page,
                    confidence=best_conf,
                )],
                computed_value=value,
                confidence=best_conf,
                explanation=f"{item_label} = {best_row.label}",
            )
            expressions.append(expr)

        return expressions

    def detect_unit_conversion(
        self,
        extracted_rows: list[ExtractedRow],
        known_values: dict[str, float] | None = None,
        note_text: str = "",
    ) -> float:
        """Detect if values need scaling (thousands/millions).

        Checks header text, page text, and optionally XBRL known values.

        Args:
            extracted_rows: Extracted data rows.
            known_values: Optional dict of known correct values for validation.
            note_text: Page text to scan for unit indicators.

        Returns:
            Scale factor (1, 1000, or 1000000).
        """
        # Check note text for unit indicators
        if note_text:
            text_lower = note_text.lower()
            if re.search(r'\bin millions?\b', text_lower):
                return 1_000_000
            if re.search(r'\bin thousands?\b', text_lower):
                return 1_000
            if re.search(r"\b'000\b|\bthousands\b", text_lower):
                return 1_000

        # If known values provided, compute the ratio
        if known_values and extracted_rows:
            for row in extracted_rows:
                if row.label in known_values and row.values:
                    extracted_val = row.values[0]
                    known_val = known_values[row.label]
                    if extracted_val and known_val and abs(extracted_val) > 0:
                        ratio = known_val / extracted_val
                        if 900 < ratio < 1100:
                            return 1000
                        if 900_000 < ratio < 1_100_000:
                            return 1_000_000

        return 1.0

    # ─── Private helpers ──────────────────────────────────────

    def _detect_hierarchy(
        self, rows: list[ExtractedRow]
    ) -> dict[int, list[int]]:
        """Detect parent-child relationships via indentation.

        A total row (indent 0) with preceding indented rows (indent 1+)
        means those indented rows are its children.

        Returns:
            Dict mapping parent_row_index → [child_row_indices].
        """
        parent_children: dict[int, list[int]] = {}
        pending_children: list[int] = []

        for i, row in enumerate(rows):
            if row.is_header:
                pending_children = []
                continue

            if row.indent_level > 0 and not row.is_total:
                pending_children.append(i)
            elif row.is_total and pending_children:
                parent_children[i] = pending_children.copy()
                pending_children = []
            elif row.indent_level == 0:
                pending_children = []

        return parent_children

    def _try_sum_expression(
        self,
        target_id: str,
        target_label: str,
        total_row: ExtractedRow,
        children_indices: list[int],
        extracted_rows: list[ExtractedRow],
        period_index: int,
        confidence: float,
    ) -> MappingExpression | None:
        """Try to build a SUM expression from children.

        Validates that children actually sum to the total value.
        """
        total_value = self._get_value(total_row, period_index)
        if total_value is None:
            return None

        sources = []
        children_sum = 0.0
        for child_idx in children_indices:
            child_row = extracted_rows[child_idx]
            child_val = self._get_value(child_row, period_index)
            if child_val is None:
                continue
            children_sum += child_val
            sources.append(SourceRef(
                row_index=child_idx,
                label=child_row.label,
                value=child_val,
                page=child_row.page,
                confidence=confidence * 0.95,  # Slightly less confident for components
            ))

        if not sources:
            return None

        # Validate sum matches total (within tolerance)
        if abs(total_value) > 0:
            diff_ratio = abs(children_sum - total_value) / abs(total_value)
        else:
            diff_ratio = abs(children_sum - total_value)

        if diff_ratio <= self.tolerance:
            # Sum matches — create SUM expression
            source_labels = " + ".join(s.label for s in sources)
            return MappingExpression(
                target_item_id=target_id,
                target_label=target_label,
                expression_type=ExpressionType.SUM,
                sources=sources,
                computed_value=children_sum,
                confidence=confidence * 0.9,  # SUM expressions slightly less confident
                explanation=f"{target_label} = {source_labels}",
            )

        # Sum doesn't match — fall back to direct mapping to total row
        logger.debug(
            "SUM mismatch for %s: children=%s, total=%s (diff=%s%%)",
            target_label, children_sum, total_value, f"{diff_ratio*100:.1f}",
        )
        return None

    def _discover_sum_expressions(
        self,
        extracted_rows: list[ExtractedRow],
        period_index: int,
    ) -> dict[int, object]:
        if self._milp_solver is None:
            return {}

        from app.ml.ng_milp import CellValue

        cells: list[CellValue] = []
        for row in extracted_rows:
            value = self._get_value(row, period_index)
            if value is None:
                continue

            cells.append(
                CellValue(
                    row=row.index,
                    col=period_index,
                    value=Decimal(str(value)),
                    label=row.label,
                    indent_level=row.indent_level,
                    is_total=row.is_total,
                    page=row.page,
                )
            )

        try:
            return {
                expression.sum_cell.row: expression
                for expression in self._milp_solver.solve(cells)
            }
        except Exception:
            logger.exception("NG-MILP solve failed; falling back to heuristic expression engine")
            return {}

    def _build_ng_milp_expression(
        self,
        target_id: str,
        target_label: str,
        expression,
        confidence: float,
    ) -> MappingExpression:
        sources = [
            SourceRef(
                row_index=cell.row,
                label=cell.label,
                value=float(cell.value),
                page=cell.page,
                confidence=max(confidence * 0.95, expression.confidence),
            )
            for cell in expression.addends
        ]
        source_labels = " + ".join(source.label for source in sources)
        solver_confidence = max(confidence * 0.9, expression.confidence)

        return MappingExpression(
            target_item_id=target_id,
            target_label=target_label,
            expression_type=ExpressionType.SUM,
            sources=sources,
            computed_value=float(expression.computed_value),
            confidence=round(min(solver_confidence, 0.99), 4),
            explanation=f"{target_label} = {source_labels}",
        )

    @staticmethod
    def _get_value(row: ExtractedRow, period_index: int = 0) -> float | None:
        """Get value at given period index, safely."""
        if not row.values:
            return None
        if period_index < len(row.values):
            return row.values[period_index]
        return row.values[0] if row.values else None


# ─── Autofill: Reuse previous period's mappings ─────────────────


class ExpressionMemory:
    """Remembers mapping expressions from previous spreads.

    For repeat customers, the same document structure appears each period.
    This class caches the expression patterns (not values) and auto-applies
    them to new documents from the same customer.
    """

    def __init__(self):
        self._memory: dict[str, list[dict]] = {}  # tenant_customer → expressions

    def remember(self, tenant_id: str, customer_id: str, expressions: list[MappingExpression]):
        """Store expression patterns for future reuse."""
        key = f"{tenant_id}:{customer_id}"
        self._memory[key] = [
            {
                "target_item_id": e.target_item_id,
                "expression_type": e.expression_type.value,
                "source_labels": [s.label for s in e.sources],
                "scale_factor": e.scale_factor,
            }
            for e in expressions
            if e.confidence >= 0.7  # Only remember confident mappings
        ]
        logger.info(
            "Stored %d expression patterns for %s",
            len(self._memory[key]), key,
        )

    def recall(self, tenant_id: str, customer_id: str) -> list[dict] | None:
        """Retrieve stored expression patterns."""
        key = f"{tenant_id}:{customer_id}"
        patterns = self._memory.get(key)
        if patterns:
            logger.info("Found %d stored expressions for %s", len(patterns), key)
        return patterns

    def apply_remembered_patterns(
        self,
        patterns: list[dict],
        extracted_rows: list[ExtractedRow],
        model_items: list[dict],
        period_index: int = 0,
    ) -> list[MappingExpression]:
        """Apply stored expression patterns to new extraction.

        Matches by source labels (label similarity), not row indices,
        since row positions may shift between periods.

        Returns:
            List of auto-filled MappingExpression.
        """
        # Build label → row index map
        label_to_row = {}
        for row in extracted_rows:
            label_lower = row.label.lower().strip()
            label_to_row[label_lower] = row

        autofilled = []
        for pattern in patterns:
            target_id = pattern["target_item_id"]
            expr_type = pattern["expression_type"]
            source_labels = pattern["source_labels"]

            # Find matching rows by label
            matched_sources = []
            for src_label in source_labels:
                row = label_to_row.get(src_label.lower().strip())
                if row:
                    value = row.values[period_index] if period_index < len(row.values) else None
                    matched_sources.append(SourceRef(
                        row_index=row.index,
                        label=row.label,
                        value=value,
                        page=row.page,
                        confidence=0.85,  # Autofill confidence
                    ))

            if len(matched_sources) == len(source_labels):
                # All sources found — apply pattern
                target_label = target_id
                for item in model_items:
                    if item["id"] == target_id:
                        target_label = item["label"]
                        break

                if expr_type == ExpressionType.SUM.value:
                    computed = sum(s.value for s in matched_sources if s.value is not None)
                elif expr_type == ExpressionType.NEGATE.value:
                    v = matched_sources[0].value if matched_sources else None
                    computed = -v if v is not None else None
                else:
                    computed = matched_sources[0].value if matched_sources else None

                autofilled.append(MappingExpression(
                    target_item_id=target_id,
                    target_label=target_label,
                    expression_type=ExpressionType(expr_type),
                    sources=matched_sources,
                    scale_factor=pattern.get("scale_factor", 1.0),
                    computed_value=computed,
                    confidence=0.85,
                    explanation=f"Auto-filled from previous period",
                ))

        logger.info(
            "Autofill: matched %d/%d patterns",
            len(autofilled), len(patterns),
        )
        return autofilled
