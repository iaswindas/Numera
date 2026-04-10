"""XBRL financial filing parser for auto-labeling training data.

Parses XBRL structured filings from SEC EDGAR to extract concept-to-value
mappings. These provide free ground truth labels for training the semantic
matching model.

Usage (in Colab notebook):
    from scripts.xbrl_parser import XbrlParser
    parser = XbrlParser()
    facts = parser.parse_filing("/path/to/filing.xml")
"""

import json
import logging
import re
from pathlib import Path
from typing import Optional
from xml.etree import ElementTree as ET

logger = logging.getLogger(__name__)

# XBRL concept → zone type mapping
CONCEPT_ZONE_MAP = {
    # Balance Sheet
    "Assets": "BALANCE_SHEET",
    "AssetsCurrent": "BALANCE_SHEET",
    "AssetsNoncurrent": "BALANCE_SHEET",
    "Liabilities": "BALANCE_SHEET",
    "LiabilitiesCurrent": "BALANCE_SHEET",
    "LiabilitiesNoncurrent": "BALANCE_SHEET",
    "StockholdersEquity": "BALANCE_SHEET",
    "CashAndCashEquivalents": "BALANCE_SHEET",
    "AccountsReceivableNet": "BALANCE_SHEET",
    "Goodwill": "BALANCE_SHEET",
    "PropertyPlantAndEquipmentNet": "BALANCE_SHEET",
    # Income Statement
    "Revenue": "INCOME_STATEMENT",
    "Revenues": "INCOME_STATEMENT",
    "RevenueFromContractWithCustomer": "INCOME_STATEMENT",
    "CostOfRevenue": "INCOME_STATEMENT",
    "GrossProfit": "INCOME_STATEMENT",
    "OperatingIncomeLoss": "INCOME_STATEMENT",
    "NetIncomeLoss": "INCOME_STATEMENT",
    "EarningsPerShareBasic": "INCOME_STATEMENT",
    "EarningsPerShareDiluted": "INCOME_STATEMENT",
    # Cash Flow
    "NetCashProvidedByOperatingActivities": "CASH_FLOW",
    "NetCashProvidedByInvestingActivities": "CASH_FLOW",
    "NetCashProvidedByFinancingActivities": "CASH_FLOW",
    "DepreciationAndAmortization": "CASH_FLOW",
}


class XbrlParser:
    """Parse XBRL filings to extract structured financial facts."""

    # Common XBRL namespace prefixes
    NS = {
        "xbrli": "http://www.xbrl.org/2003/instance",
        "us-gaap": "http://fasb.org/us-gaap/2024",
        "ifrs-full": "http://xbrl.ifrs.org/taxonomy/2024-01-01/ifrs-full",
    }

    def parse_filing(self, filepath: str) -> list[dict]:
        """Parse an XBRL instance document and extract financial facts.

        Args:
            filepath: Path to XBRL (.xml) file.

        Returns:
            List of fact dicts with keys: concept, value, context, unit, zone_type.
        """
        try:
            tree = ET.parse(filepath)
            root = tree.getroot()
        except Exception:
            logger.warning("Failed to parse XBRL: %s", filepath)
            return []

        facts = []
        for element in root.iter():
            tag = element.tag
            # Extract local name from namespace-qualified tag
            local_name = tag.split("}")[-1] if "}" in tag else tag

            if element.text and element.text.strip():
                value = element.text.strip()
                context_ref = element.get("contextRef", "")
                unit_ref = element.get("unitRef", "")
                decimals = element.get("decimals", "")

                zone_type = CONCEPT_ZONE_MAP.get(local_name)

                facts.append({
                    "concept": local_name,
                    "value": value,
                    "context_ref": context_ref,
                    "unit_ref": unit_ref,
                    "decimals": decimals,
                    "zone_type": zone_type,
                    "source_file": str(filepath),
                })

        logger.info("Parsed %d facts from %s", len(facts), filepath)
        return facts

    def extract_mapping_pairs(
        self, facts: list[dict]
    ) -> list[dict]:
        """Convert XBRL facts into (source_text, target_label) training pairs.

        These pairs train the Sentence-BERT semantic matcher.

        Returns:
            List of dicts with source_text, target_label, zone_type, value.
        """
        pairs = []
        for fact in facts:
            if fact["zone_type"] and fact["value"]:
                # CamelCase → "Camel Case" for readable label
                label = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", fact["concept"])
                pairs.append({
                    "source_text": label,
                    "target_label": fact["concept"],
                    "zone_type": fact["zone_type"],
                    "value": fact["value"],
                })
        return pairs

    def batch_parse(
        self, directory: str, output_path: Optional[str] = None
    ) -> list[dict]:
        """Parse all XBRL files in a directory.

        Args:
            directory: Path to directory containing XBRL files.
            output_path: Optional path to save results as JSON.

        Returns:
            All extracted facts.
        """
        dir_path = Path(directory)
        all_facts = []

        for filepath in dir_path.glob("**/*.xml"):
            facts = self.parse_filing(str(filepath))
            all_facts.extend(facts)

        if output_path:
            Path(output_path).parent.mkdir(parents=True, exist_ok=True)
            with open(output_path, "w") as f:
                json.dump(all_facts, f, indent=2)
            logger.info("Saved %d facts to %s", len(all_facts), output_path)

        return all_facts
