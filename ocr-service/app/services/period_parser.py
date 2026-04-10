"""Period, currency, and unit detection from table header text.

Rule-based parser that extracts reporting periods (dates/years),
currency codes, and unit scales from financial table headers and
surrounding text.
"""

import re
from typing import Optional


class PeriodParser:
    """Extract financial metadata from table header text."""

    # --- Year / Date patterns (ordered by specificity) ---
    YEAR_PATTERNS = [
        # "Year ended 31 December 2024", "Period ended 30 June 2023"
        re.compile(
            r"(?:year|period)\s+ended\s+(\d{1,2}\s+\w+\s+\d{4})", re.IGNORECASE
        ),
        # "31 December 2024", "30 June 2023"
        re.compile(r"\b(\d{1,2}\s+(?:January|February|March|April|May|June|"
                   r"July|August|September|October|November|December)\s+\d{4})\b",
                   re.IGNORECASE),
        # "FY2024", "FY 2024"
        re.compile(r"\b(FY\s*\d{4})\b", re.IGNORECASE),
        # "Q1 2024", "Q4 2023"
        re.compile(r"\b(Q[1-4]\s*\d{4})\b", re.IGNORECASE),
        # Bare year "2024", "2023" (only match 2000–2099)
        re.compile(r"\b(20\d{2})\b"),
        # Date formats: "31/12/2024", "12-31-2024"
        re.compile(r"\b(\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b"),
    ]

    # --- Currency symbols and codes ---
    CURRENCY_MAP = {
        "€": "EUR",
        "£": "GBP",
        "$": "USD",
        "US$": "USD",
        "US Dollar": "USD",
        "AED": "AED",
        "SAR": "SAR",
        "CHF": "CHF",
        "QAR": "QAR",
        "BHD": "BHD",
        "KWD": "KWD",
        "OMR": "OMR",
        "EUR": "EUR",
        "GBP": "GBP",
        "USD": "USD",
        "INR": "INR",
        "CNY": "CNY",
        "JPY": "JPY",
    }

    # --- Unit patterns (ordered by specificity) ---
    UNIT_PATTERNS = [
        (re.compile(r"in\s+billions|in\s+bn", re.IGNORECASE), "billions"),
        (re.compile(r"in\s+millions|in\s+m(?:illion)?s?\b|\(€m\)|\(£m\)|\(m\)", re.IGNORECASE), "millions"),
        (re.compile(r"in\s+thousands|in\s+'000s?|'000|\(000\)", re.IGNORECASE), "thousands"),
    ]

    def extract_periods(self, text: str) -> list[str]:
        """Extract all detected reporting periods from text.

        Returns a deduplicated list of period strings, e.g.
        ["31 December 2024", "31 December 2023"] or ["2024", "2023"].
        """
        if not text:
            return []

        found: list[str] = []
        seen: set[str] = set()
        for pattern in self.YEAR_PATTERNS:
            for match in pattern.finditer(text):
                value = match.group(1).strip()
                normalised = value.lower()
                if normalised not in seen:
                    seen.add(normalised)
                    found.append(value)

        return found

    def extract_currency(self, text: str) -> Optional[str]:
        """Detect the currency used in the table.

        Returns an ISO 4217 currency code or None.
        """
        if not text:
            return None

        # Check for symbol or code in the text
        for symbol, code in self.CURRENCY_MAP.items():
            if symbol in text:
                return code

        return None

    def extract_unit(self, text: str) -> Optional[str]:
        """Detect the unit scale (thousands, millions, billions).

        Returns a human-readable unit string or None.
        """
        if not text:
            return None

        for pattern, unit in self.UNIT_PATTERNS:
            if pattern.search(text):
                return unit

        return None
