"""
Optional helper: print Firestore-ready article dicts from seed/articles.json.
Import manually in Firebase Console, or use Firebase Admin later.
"""
from __future__ import annotations

import json
from pathlib import Path


def main() -> None:
    path = Path(__file__).with_name("articles.json")
    articles = json.loads(path.read_text(encoding="utf-8"))
    print(f"Loaded {len(articles)} articles.\n")
    for i, article in enumerate(articles, start=1):
        print(f"--- Article {i}: {article['title']}")
        for key, value in article.items():
            print(f"  {key}: {value}")
        print()


if __name__ == "__main__":
    main()
