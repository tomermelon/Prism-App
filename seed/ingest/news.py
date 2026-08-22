"""Fetch headlines from NewsAPI.org."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone

import requests

NEWS_BASE = "https://newsapi.org/v2"


@dataclass
class RawArticle:
    title: str
    description: str
    source: str
    url: str
    published_at_ms: int


def _parse_time(value: str | None) -> int:
    if not value:
        return int(datetime.now(tz=timezone.utc).timestamp() * 1000)
    try:
        # NewsAPI: 2024-01-01T12:00:00Z
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return int(dt.timestamp() * 1000)
    except ValueError:
        return int(datetime.now(tz=timezone.utc).timestamp() * 1000)


def _normalize(item: dict) -> RawArticle | None:
    title = (item.get("title") or "").strip()
    url = (item.get("url") or "").strip()
    if not title or not url or title == "[Removed]":
        return None
    source = ((item.get("source") or {}).get("name") or "Unknown").strip()
    description = (item.get("description") or item.get("content") or "").strip()
    if description.endswith("…") or description.endswith("..."):
        pass
    # Drop NewsAPI truncated marker junk if present
    if "chars]" in description:
        description = description.split("…")[0].strip() or description
    return RawArticle(
        title=title,
        description=description or title,
        source=source,
        url=url,
        published_at_ms=_parse_time(item.get("publishedAt")),
    )


def _get(path: str, api_key: str, params: dict) -> list[dict]:
    resp = requests.get(
        f"{NEWS_BASE}/{path}",
        params={**params, "apiKey": api_key},
        timeout=30,
    )
    if resp.status_code != 200:
        raise RuntimeError(f"NewsAPI {path} failed ({resp.status_code}): {resp.text[:300]}")
    data = resp.json()
    if data.get("status") != "ok":
        raise RuntimeError(f"NewsAPI error: {data}")
    return data.get("articles") or []


def fetch_raw_articles(api_key: str, limit: int = 20, country: str = "us") -> list[RawArticle]:
    """Pull US top headlines plus a small international set, then cap at `limit`."""
    us_n = max(1, int(limit * 0.7))
    intl_n = max(1, limit - us_n)

    us_items = _get(
        "top-headlines",
        api_key,
        {"country": country, "pageSize": min(us_n + 5, 100)},
    )
    intl_items = _get(
        "top-headlines",
        api_key,
        {
            "sources": "bbc-news,reuters,al-jazeera-english",
            "pageSize": min(intl_n + 5, 100),
        },
    )

    seen: set[str] = set()
    out: list[RawArticle] = []

    for bucket in (us_items[:us_n], intl_items[:intl_n], us_items[us_n:], intl_items[intl_n:]):
        for item in bucket:
            art = _normalize(item)
            if art is None or art.url in seen:
                continue
            seen.add(art.url)
            out.append(art)
            if len(out) >= limit:
                return out
    return out
