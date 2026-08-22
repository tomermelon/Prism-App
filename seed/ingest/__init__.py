"""Prism news ingest: fetch → classify → Firestore."""

from .pipeline import run_ingest

__all__ = ["run_ingest"]
