"""Load secrets from seed/.env."""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

SEED_DIR = Path(__file__).resolve().parent.parent


def load_config() -> dict[str, str]:
    load_dotenv(SEED_DIR / ".env")

    news_key = os.getenv("NEWSAPI_KEY", "").strip()
    gemini_key = os.getenv("GEMINI_API_KEY", "").strip()
    project_id = os.getenv("FIREBASE_PROJECT_ID", "prism-2f8f9").strip()
    creds = os.getenv("GOOGLE_APPLICATION_CREDENTIALS", "").strip()

    if not news_key:
        raise SystemExit("Missing NEWSAPI_KEY in seed/.env")
    if not gemini_key:
        raise SystemExit("Missing GEMINI_API_KEY in seed/.env")
    if not creds:
        raise SystemExit("Missing GOOGLE_APPLICATION_CREDENTIALS in seed/.env")

    cred_path = Path(creds)
    if not cred_path.is_absolute():
        cred_path = SEED_DIR / cred_path
    if not cred_path.is_file():
        raise SystemExit(f"Service account JSON not found: {cred_path}")

    return {
        "newsapi_key": news_key,
        "gemini_key": gemini_key,
        "project_id": project_id,
        "credentials_path": str(cred_path),
    }
