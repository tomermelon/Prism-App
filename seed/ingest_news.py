#!/usr/bin/env python3
"""
Run Prism news ingest manually:

  cd seed
  python -m venv .venv
  .venv\\Scripts\\activate          # Windows
  pip install -r requirements.txt
  python ingest_news.py

Optional:
  python ingest_news.py --limit 40 --country us
  python ingest_news.py --dry-run
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# Allow `python ingest_news.py` from the seed/ folder
sys.path.insert(0, str(Path(__file__).resolve().parent))

from ingest.pipeline import run_ingest  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="Ingest news into Prism Firestore")
    parser.add_argument("--limit", type=int, default=40, help="Max articles to process")
    parser.add_argument("--country", default="us", help="NewsAPI country for national headlines")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Fetch + classify only; do not write Firestore",
    )
    args = parser.parse_args()
    run_ingest(limit=args.limit, country=args.country, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
