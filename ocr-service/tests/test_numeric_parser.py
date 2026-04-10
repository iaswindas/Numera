"""Tests for numeric parser."""

import pytest
from app.utils.numeric_parser import parse_financial_number


class TestParseFinancialNumber:
    def test_simple_integer(self):
        assert parse_financial_number("1234567") == 1234567.0

    def test_comma_thousands(self):
        assert parse_financial_number("1,234,567") == 1234567.0

    def test_period_thousands_european(self):
        assert parse_financial_number("1.234.567") == 1234567.0

    def test_english_decimal(self):
        assert parse_financial_number("1,234.56") == 1234.56

    def test_european_decimal(self):
        assert parse_financial_number("1.234,56") == 1234.56

    def test_parenthesized_negative(self):
        assert parse_financial_number("(1,234)") == -1234.0

    def test_dash_negative(self):
        assert parse_financial_number("-1,234") == -1234.0

    def test_dash_na(self):
        assert parse_financial_number("-") is None
        assert parse_financial_number("—") is None

    def test_empty(self):
        assert parse_financial_number("") is None

    def test_multiplier_m(self):
        assert parse_financial_number("1.2M") == 1200000.0

    def test_multiplier_k(self):
        assert parse_financial_number("500K") == 500000.0

    def test_currency_symbol_stripped(self):
        assert parse_financial_number("$1,234") == 1234.0
        assert parse_financial_number("€1,234") == 1234.0
