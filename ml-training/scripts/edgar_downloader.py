"""SEC EDGAR annual report downloader.

Downloads 10-K and 20-F filings (annual reports) from SEC EDGAR
full-text search API for IFRS/US-GAAP training data collection.

Usage (in Colab notebook):
    from scripts.edgar_downloader import EdgarDownloader
    downloader = EdgarDownloader(user_agent="YourName your@email.com")
    downloader.download_10k_filings(
        output_dir="/content/drive/MyDrive/numera-ml/data/edgar",
        max_companies=500,
    )
"""

import json
import logging
import time
from pathlib import Path
from typing import Optional

import requests

logger = logging.getLogger(__name__)

EDGAR_FULL_TEXT_SEARCH = "https://efts.sec.gov/LATEST/search-index"
EDGAR_FILING_API = "https://data.sec.gov/submissions/CIK{cik}.json"
EDGAR_ARCHIVE_BASE = "https://www.sec.gov/Archives/edgar/data"


class EdgarDownloader:
    """Download annual reports from SEC EDGAR."""

    def __init__(self, user_agent: str):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": user_agent,
            "Accept-Encoding": "gzip, deflate",
        })

    def get_company_filings(
        self, cik: str, filing_type: str = "10-K", max_results: int = 5
    ) -> list[dict]:
        """Get filing metadata for a company by CIK number."""
        cik_padded = cik.zfill(10)
        url = EDGAR_FILING_API.format(cik=cik_padded)

        try:
            resp = self.session.get(url)
            resp.raise_for_status()
            data = resp.json()
        except Exception:
            logger.warning("Failed to fetch filings for CIK %s", cik)
            return []

        recent = data.get("filings", {}).get("recent", {})
        forms = recent.get("form", [])
        accessions = recent.get("accessionNumber", [])
        dates = recent.get("filingDate", [])
        primary_docs = recent.get("primaryDocument", [])

        filings = []
        for i, form in enumerate(forms):
            if form == filing_type and len(filings) < max_results:
                filings.append({
                    "cik": cik,
                    "form": form,
                    "accession": accessions[i].replace("-", ""),
                    "filing_date": dates[i],
                    "primary_document": primary_docs[i],
                })

        time.sleep(0.1)  # SEC rate limit: 10 req/s
        return filings

    def download_filing(
        self, filing: dict, output_dir: Path
    ) -> Optional[Path]:
        """Download the primary document of a filing."""
        cik = filing["cik"]
        accession = filing["accession"]
        doc_name = filing["primary_document"]

        url = f"{EDGAR_ARCHIVE_BASE}/{cik}/{accession}/{doc_name}"
        output_path = output_dir / f"{cik}_{filing['filing_date']}_{doc_name}"

        if output_path.exists():
            return output_path

        try:
            resp = self.session.get(url, timeout=30)
            resp.raise_for_status()
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_bytes(resp.content)
            logger.info("Downloaded: %s", output_path.name)
            time.sleep(0.1)
            return output_path
        except Exception:
            logger.warning("Failed to download %s", url)
            return None

    def download_10k_filings(
        self,
        output_dir: str,
        cik_list: Optional[list[str]] = None,
        max_companies: int = 500,
        filings_per_company: int = 2,
    ) -> list[dict]:
        """Batch download 10-K filings for training data.

        Args:
            output_dir: Directory to save PDFs.
            cik_list: Optional list of CIK numbers. If None, uses a built-in list.
            max_companies: Maximum number of companies to process.
            filings_per_company: Number of annual reports per company.

        Returns:
            List of download metadata dicts.
        """
        out = Path(output_dir)
        out.mkdir(parents=True, exist_ok=True)

        if cik_list is None:
            # Default: large-cap US-listed companies with IFRS/GAAP filings
            cik_list = self._get_default_cik_list()

        metadata = []
        for i, cik in enumerate(cik_list[:max_companies]):
            filings = self.get_company_filings(
                cik, filing_type="10-K", max_results=filings_per_company
            )
            for filing in filings:
                path = self.download_filing(filing, out / "raw_pdfs")
                if path:
                    filing["local_path"] = str(path)
                    metadata.append(filing)

            if (i + 1) % 50 == 0:
                logger.info("Progress: %d/%d companies", i + 1, min(max_companies, len(cik_list)))

        # Save metadata
        meta_path = out / "metadata.csv"
        with open(meta_path, "w") as f:
            if metadata:
                f.write(",".join(metadata[0].keys()) + "\n")
                for m in metadata:
                    f.write(",".join(str(v) for v in m.values()) + "\n")

        logger.info("Downloaded %d filings from %d companies", len(metadata), max_companies)
        return metadata

    @staticmethod
    def _get_default_cik_list() -> list[str]:
        """Return a starter list of CIK numbers for large-cap companies."""
        # Top 20 for initial testing — expand via EDGAR company search
        return [
            "320193",   # Apple
            "789019",   # Microsoft
            "1652044",  # Alphabet (Google)
            "1018724",  # Amazon
            "1326801",  # Meta (Facebook)
            "1045810",  # NVIDIA
            "1318605",  # Tesla
            "78003",    # Pfizer
            "200406",   # Johnson & Johnson
            "34088",    # ExxonMobil
            "21344",    # Coca-Cola
            "93410",    # Chevron
            "732717",   # AT&T
            "12927",    # Boeing
            "51143",    # IBM
            "315066",   # Intel
            "886982",   # GS (Goldman Sachs)
            "70858",    # Bank of America
            "831001",   # Citigroup
            "19617",    # JPMorgan Chase
        ]
