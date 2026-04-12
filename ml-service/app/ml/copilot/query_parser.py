"""Natural language → structured filter parameters for dashboard queries.

Converts plain-English questions into typed filter dicts that the
spreading dashboard and covenant screens can consume directly.
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from datetime import date, timedelta
from typing import Any

logger = logging.getLogger(__name__)

# ------------------------------------------------------------------
# Output model
# ------------------------------------------------------------------


@dataclass
class ParsedQuery:
    """Structured filter parameters extracted from a natural-language query."""

    intent: str  # e.g. "spread_lookup", "covenant_breach", "customer_search"
    filters: dict[str, Any] = field(default_factory=dict)
    sort_by: str | None = None
    sort_order: str = "desc"
    limit: int = 20
    raw_query: str = ""
    confidence: float = 0.0


# ------------------------------------------------------------------
# Pattern definitions
# ------------------------------------------------------------------

_INTENT_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("spread_lookup", re.compile(
        r"(?:show|get|find|display|list)\s+(?:me\s+)?(?:the\s+)?spread(?:s|ing)?",
        re.IGNORECASE,
    )),
    ("covenant_breach", re.compile(
        r"(?:covenant|breach|violation|non-?compliance|threshold)",
        re.IGNORECASE,
    )),
    ("customer_search", re.compile(
        r"(?:customer|borrower|client)\s+(?:named?|called|for)\s+",
        re.IGNORECASE,
    )),
    ("ratio_analysis", re.compile(
        r"(?:ratio|leverage|coverage|debt.+equity|current\s+ratio|dscr|ltv)",
        re.IGNORECASE,
    )),
    ("trend_analysis", re.compile(
        r"(?:trend|over\s+time|year.over.year|quarter.over.quarter|growth|decline)",
        re.IGNORECASE,
    )),
    ("document_search", re.compile(
        r"(?:document|pdf|statement|report|filing)\s+(?:for|from|about)",
        re.IGNORECASE,
    )),
    ("risk_summary", re.compile(
        r"(?:risk|exposure|concentration|portfolio)\s+(?:summary|overview|report)",
        re.IGNORECASE,
    )),
]

_DATE_PATTERNS = [
    (re.compile(r"last\s+(\d+)\s+days?", re.IGNORECASE), "days"),
    (re.compile(r"last\s+(\d+)\s+months?", re.IGNORECASE), "months"),
    (re.compile(r"last\s+(\d+)\s+years?", re.IGNORECASE), "years"),
    (re.compile(r"since\s+(\d{4}[-/]\d{2}[-/]\d{2})", re.IGNORECASE), "since"),
    (re.compile(r"(?:in|for|during)\s+(\d{4})", re.IGNORECASE), "year"),
    (re.compile(r"(?:this|current)\s+year", re.IGNORECASE), "this_year"),
    (re.compile(r"(?:this|current)\s+quarter", re.IGNORECASE), "this_quarter"),
]

_STATUS_PATTERN = re.compile(
    r"\b(draft|submitted|approved|pushed|pending|rejected)\b",
    re.IGNORECASE,
)

_CUSTOMER_NAME_PATTERN = re.compile(
    r"(?:customer|borrower|client)\s+(?:named?|called|for)\s+[\"']?([A-Za-z0-9\s&.,-]+?)[\"']?(?:\s+(?:in|since|from|with|over)|$)",
    re.IGNORECASE,
)

_LIMIT_PATTERN = re.compile(r"(?:top|first|last|limit)\s+(\d+)", re.IGNORECASE)

_SORT_PATTERN = re.compile(
    r"(?:sort|order)\s+(?:by\s+)?(\w+)\s*(asc|desc)?",
    re.IGNORECASE,
)


# ------------------------------------------------------------------
# Parser
# ------------------------------------------------------------------


class QueryParser:
    """Stateless NL → structured filter parser."""

    def parse(self, query: str) -> ParsedQuery:
        """Parse a natural-language query into structured filter parameters."""
        query = query.strip()
        if not query:
            return ParsedQuery(intent="unknown", raw_query=query, confidence=0.0)

        intent, intent_conf = self._detect_intent(query)
        filters = self._extract_filters(query)
        sort_by, sort_order = self._extract_sort(query)
        limit = self._extract_limit(query)

        return ParsedQuery(
            intent=intent,
            filters=filters,
            sort_by=sort_by,
            sort_order=sort_order,
            limit=limit,
            raw_query=query,
            confidence=intent_conf,
        )

    # ------------------------------------------------------------------

    def _detect_intent(self, query: str) -> tuple[str, float]:
        best_intent = "general"
        best_conf = 0.3

        for intent_name, pattern in _INTENT_PATTERNS:
            match = pattern.search(query)
            if match:
                span_ratio = len(match.group(0)) / max(len(query), 1)
                conf = min(0.95, 0.6 + span_ratio)
                if conf > best_conf:
                    best_intent = intent_name
                    best_conf = conf

        return best_intent, round(best_conf, 2)

    def _extract_filters(self, query: str) -> dict[str, Any]:
        filters: dict[str, Any] = {}

        # Date filters
        date_range = self._extract_date_range(query)
        if date_range:
            filters["date_from"] = date_range[0]
            filters["date_to"] = date_range[1]

        # Status
        status_match = _STATUS_PATTERN.search(query)
        if status_match:
            filters["status"] = status_match.group(1).upper()

        # Customer name
        cust_match = _CUSTOMER_NAME_PATTERN.search(query)
        if cust_match:
            filters["customer_name"] = cust_match.group(1).strip()

        return filters

    def _extract_date_range(self, query: str) -> tuple[str, str] | None:
        today = date.today()

        for pattern, kind in _DATE_PATTERNS:
            match = pattern.search(query)
            if not match:
                continue

            if kind == "days":
                n = int(match.group(1))
                return (today - timedelta(days=n)).isoformat(), today.isoformat()
            elif kind == "months":
                n = int(match.group(1))
                start = today.replace(day=1)
                for _ in range(n):
                    start = (start - timedelta(days=1)).replace(day=1)
                return start.isoformat(), today.isoformat()
            elif kind == "years":
                n = int(match.group(1))
                return today.replace(year=today.year - n).isoformat(), today.isoformat()
            elif kind == "since":
                return match.group(1).replace("/", "-"), today.isoformat()
            elif kind == "year":
                year = int(match.group(1))
                return f"{year}-01-01", f"{year}-12-31"
            elif kind == "this_year":
                return f"{today.year}-01-01", today.isoformat()
            elif kind == "this_quarter":
                q_start_month = ((today.month - 1) // 3) * 3 + 1
                return today.replace(month=q_start_month, day=1).isoformat(), today.isoformat()

        return None

    def _extract_sort(self, query: str) -> tuple[str | None, str]:
        match = _SORT_PATTERN.search(query)
        if match:
            return match.group(1), (match.group(2) or "desc").lower()
        return None, "desc"

    def _extract_limit(self, query: str) -> int:
        match = _LIMIT_PATTERN.search(query)
        if match:
            return min(int(match.group(1)), 100)
        return 20
