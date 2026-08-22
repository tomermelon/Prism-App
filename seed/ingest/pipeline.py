"""Orchestrate fetch → classify → write. Safe to wrap in a Cloud Function later."""

from __future__ import annotations

from .classify import classify_many
from .config import load_config
from .firestore_client import existing_urls, init_firebase, write_article
from .news import fetch_raw_articles


def run_ingest(limit: int = 40, country: str = "us", dry_run: bool = False) -> dict[str, int]:
    cfg = load_config()
    print(f"Fetching up to {limit} headlines (country={country})...")
    raw = fetch_raw_articles(cfg["newsapi_key"], limit=limit, country=country)
    print(f"Got {len(raw)} raw articles from NewsAPI.")

    db = None
    known: set[str] = set()
    if not dry_run:
        db = init_firebase(cfg["credentials_path"], cfg["project_id"])
        known = existing_urls(db)
        print(f"Firestore already has {len(known)} article URLs.")

    to_process = [a for a in raw if a.url not in known]
    skipped = len(raw) - len(to_process)
    if skipped:
        print(f"Skipping {skipped} already in Firestore.")

    written = 0
    failed = 0

    if not to_process:
        summary = {"fetched": len(raw), "written": 0, "skipped": skipped, "failed": 0}
        print(
            f"\nDone. fetched={summary['fetched']} written={summary['written']} "
            f"skipped={summary['skipped']} failed={summary['failed']}"
        )
        return summary

    labels_list = classify_many(cfg["gemini_key"], to_process)

    for article, labels in zip(to_process, labels_list, strict=True):
        print(
            f"  {article.title[:70]}\n"
            f"    -> scope={labels.scope} bias={labels.bias} "
            f"topic={labels.topic} interests={labels.interests}"
        )
        if dry_run:
            written += 1
            continue
        try:
            assert db is not None
            doc_id = write_article(db, article, labels)
            written += 1
            print(f"    wrote articles/{doc_id}")
        except Exception as exc:  # noqa: BLE001
            failed += 1
            print(f"    write failed: {exc}")

    summary = {"fetched": len(raw), "written": written, "skipped": skipped, "failed": failed}
    print(
        f"\nDone. fetched={summary['fetched']} written={summary['written']} "
        f"skipped={summary['skipped']} failed={summary['failed']}"
    )
    return summary
