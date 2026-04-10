"""Tests for period/currency/unit parser."""

import pytest
from app.services.period_parser import PeriodParser


@pytest.fixture
def parser():
    return PeriodParser()


class TestPeriodExtraction:
    def test_extract_years(self, parser):
        text = "Revenue for 2024 and 2023"
        periods = parser.extract_periods(text)
        assert "2024" in periods
        assert "2023" in periods

    def test_extract_full_date(self, parser):
        text = "Year ended 31 December 2024"
        periods = parser.extract_periods(text)
        assert any("31 December 2024" in p for p in periods)

    def test_extract_fy_format(self, parser):
        text = "FY2024 results"
        periods = parser.extract_periods(text)
        assert any("FY2024" in p for p in periods)

    def test_extract_quarter(self, parser):
        text = "Q3 2024 results"
        periods = parser.extract_periods(text)
        assert any("Q3" in p for p in periods)


class TestCurrencyExtraction:
    def test_extract_euro_symbol(self, parser):
        assert parser.extract_currency("Revenue (€)") == "EUR"

    def test_extract_pound_symbol(self, parser):
        assert parser.extract_currency("Amounts in £") == "GBP"

    def test_extract_currency_code(self, parser):
        assert parser.extract_currency("All amounts in AED") == "AED"

    def test_no_currency(self, parser):
        assert parser.extract_currency("Revenue") is None


class TestUnitExtraction:
    def test_extract_thousands(self, parser):
        assert parser.extract_unit("in thousands") == "thousands"
        assert parser.extract_unit("(000)") == "thousands"

    def test_extract_millions(self, parser):
        assert parser.extract_unit("in millions") == "millions"
        assert parser.extract_unit("(€m)") == "millions"

    def test_extract_billions(self, parser):
        assert parser.extract_unit("in billions") == "billions"

    def test_no_unit(self, parser):
        assert parser.extract_unit("Revenue") is None
