"""OCR text cleaning and normalisation utilities.

Post-processes raw OCR output to improve text quality for downstream
zone classification and semantic matching.
"""

import re
import unicodedata


def normalise_whitespace(text: str) -> str:
    """Collapse multiple whitespace characters into single spaces."""
    return re.sub(r"\s+", " ", text).strip()


def remove_control_chars(text: str) -> str:
    """Remove Unicode control characters (except newlines and tabs)."""
    return "".join(
        ch for ch in text
        if unicodedata.category(ch)[0] != "C" or ch in ("\n", "\t")
    )


def fix_common_ocr_errors(text: str) -> str:
    """Fix frequent OCR misrecognitions in financial documents.

    Common substitutions:
    - 'l' for '1' in numeric contexts
    - 'O' for '0' in numeric contexts
    - 'S' for '5' in numeric contexts
    - Smart quotes → straight quotes
    - Em/en dashes → regular hyphens
    """
    # Smart quotes → straight quotes
    text = text.replace("\u2018", "'").replace("\u2019", "'")
    text = text.replace("\u201c", '"').replace("\u201d", '"')

    # Dashes → hyphens
    text = text.replace("\u2013", "-").replace("\u2014", "-")  # en/em dash

    # Fix l→1 and O→0 in numeric-looking strings (e.g., "l,234" → "1,234")
    text = re.sub(r"(?<=\d)[lI](?=[\d,.])", "1", text)
    text = re.sub(r"(?<=[\d,.])O(?=[\d,.])", "0", text)

    return text


def normalise_financial_text(text: str) -> str:
    """Full normalisation pipeline for financial OCR text."""
    text = remove_control_chars(text)
    text = fix_common_ocr_errors(text)
    text = normalise_whitespace(text)
    return text


def strip_note_references(text: str) -> str:
    """Remove footnote/note references like '(Note 5)', '(5)', 'Note 12'.

    These clutter the text for semantic matching but are useful for
    document navigation.
    """
    text = re.sub(r"\(?[Nn]ote\s*\d+\)?", "", text)
    text = re.sub(r"\(\d{1,2}\)", "", text)  # Bare parenthesized numbers
    return text.strip()
