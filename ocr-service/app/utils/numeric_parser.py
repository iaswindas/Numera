"""Financial number parsing utilities.

Parses numeric values from OCR-extracted text in financial documents,
handling various formatting conventions used across IFRS reports:
  - Thousands separators (commas, periods, spaces)
  - Decimal separators (periods, commas)
  - Parenthesized negatives: (1,234) → -1234
  - Currency symbols and suffixes
  - Abbreviated values: 1.2M, 500K
"""

import re
from typing import Optional


# Multiplier suffixes
_MULTIPLIERS = {
    "k": 1_000,
    "K": 1_000,
    "m": 1_000_000,
    "M": 1_000_000,
    "mn": 1_000_000,
    "b": 1_000_000_000,
    "B": 1_000_000_000,
    "bn": 1_000_000_000,
    "t": 1_000_000_000_000,
    "T": 1_000_000_000_000,
}

# Strip these before parsing
_CURRENCY_SYMBOLS = re.compile(r"[€£$¥₹₽₩₪]")
_CURRENCY_CODES = re.compile(r"\b(?:USD|EUR|GBP|AED|SAR|CHF|QAR|BHD|KWD|INR|CNY|JPY)\b")


def parse_financial_number(text: str) -> Optional[float]:
    """Parse a financial number string into a float.

    Handles:
    - "1,234,567" → 1234567.0
    - "(1,234)"   → -1234.0
    - "1.234.567" → 1234567.0  (European format)
    - "1 234 567" → 1234567.0  (space separator)
    - "1.2M"      → 1200000.0
    - "-"          → None (dash means N/A)
    - ""           → None

    Args:
        text: Raw numeric string from OCR.

    Returns:
        Parsed float value, or None if unparseable.
    """
    if not text:
        return None

    text = text.strip()

    # Check for dash / N/A markers
    if text in ("-", "—", "–", "−", "N/A", "n/a", "nil", "Nil", ""):
        return None

    # Detect negative via parentheses: (1,234) → negative
    is_negative = False
    if text.startswith("(") and text.endswith(")"):
        is_negative = True
        text = text[1:-1].strip()
    elif text.startswith("-") or text.startswith("−"):
        is_negative = True
        text = text[1:].strip()

    # Remove currency symbols and codes
    text = _CURRENCY_SYMBOLS.sub("", text)
    text = _CURRENCY_CODES.sub("", text).strip()

    # Check for multiplier suffix (e.g., "1.2M", "500K")
    multiplier = 1.0
    for suffix, mult in sorted(_MULTIPLIERS.items(), key=lambda x: -len(x[0])):
        if text.endswith(suffix):
            text = text[: -len(suffix)].strip()
            multiplier = mult
            break

    # Remove percentage signs (handled separately by caller)
    text = text.replace("%", "").strip()

    if not text:
        return None

    # --- Determine number format ---
    # Count commas and periods to figure out which is separator vs decimal
    commas = text.count(",")
    periods = text.count(".")
    spaces = text.count(" ")

    try:
        if commas > 0 and periods > 0:
            # Both present — last one is decimal separator
            last_comma = text.rfind(",")
            last_period = text.rfind(".")
            if last_period > last_comma:
                # English format: 1,234,567.89
                text = text.replace(",", "")
            else:
                # European format: 1.234.567,89
                text = text.replace(".", "").replace(",", ".")
        elif commas == 1 and periods == 0:
            # Could be "1,234" (thousands) or "1,23" (European decimal)
            parts = text.split(",")
            if len(parts[1]) == 3:
                # Thousands separator: 1,234
                text = text.replace(",", "")
            else:
                # Decimal: 1,23
                text = text.replace(",", ".")
        elif commas > 1:
            # Multiple commas = thousands separators: 1,234,567
            text = text.replace(",", "")
        elif periods > 1:
            # Multiple periods = thousands separators: 1.234.567
            text = text.replace(".", "")

        # Remove space separators: 1 234 567
        text = text.replace(" ", "")

        value = float(text) * multiplier
        return -value if is_negative else value

    except (ValueError, OverflowError):
        return None


def format_number(value: float, decimals: int = 0) -> str:
    """Format a number with thousands separators.

    Args:
        value: Numeric value.
        decimals: Number of decimal places.

    Returns:
        Formatted string, e.g. "1,234,567".
    """
    if decimals == 0:
        return f"{value:,.0f}"
    return f"{value:,.{decimals}f}"
