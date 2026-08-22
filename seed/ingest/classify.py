"""Classify bias / scope / topic / interests with Gemini."""

from __future__ import annotations

import json
import re
import time
from dataclasses import dataclass

import requests

from .labels import BIASES, INTERESTS, SCOPES, bias_for_source
from .news import RawArticle

GEMINI_URL = (
    "https://generativelanguage.googleapis.com/v1beta/models/"
    "gemini-3.6-flash:generateContent"
)

# Free tier is often ~5 requests/minute; batch + pause stays under that.
BATCH_SIZE = 5
BATCH_PAUSE_SEC = 15


@dataclass
class ClassifiedLabels:
    bias: str
    scope: str
    topic: str
    interests: list[str]


def _fallback(article: RawArticle) -> ClassifiedLabels:
    return ClassifiedLabels(
        bias=bias_for_source(article.source),
        scope="national",
        topic="Politics",
        interests=["National", "Politics"],
    )


def _clamp_labels(data: dict, source: str) -> ClassifiedLabels:
    bias = data.get("bias") if data.get("bias") in BIASES else bias_for_source(source)
    scope = data.get("scope") if data.get("scope") in SCOPES else "national"
    topic = data.get("topic") if data.get("topic") in INTERESTS else "Politics"
    raw_interests = data.get("interests") or []
    if not isinstance(raw_interests, list):
        raw_interests = []
    interests = [i for i in raw_interests if i in INTERESTS]
    scope_tag = {"local": "Local", "national": "National", "international": "International"}[scope]
    if scope_tag not in interests:
        interests.insert(0, scope_tag)
    if topic not in interests:
        interests.append(topic)
    deduped: list[str] = []
    for i in interests:
        if i not in deduped:
            deduped.append(i)
    return ClassifiedLabels(
        bias=bias,
        scope=scope,
        topic=topic,
        interests=deduped[:4],
    )


def _extract_json(text: str) -> dict | list:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    return json.loads(text)


def _gemini_json(api_key: str, prompt: str) -> dict | list:
    resp = requests.post(
        GEMINI_URL,
        params={"key": api_key},
        json={
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0.2,
                "responseMimeType": "application/json",
            },
        },
        timeout=90,
    )
    if resp.status_code != 200:
        raise RuntimeError(f"Gemini failed ({resp.status_code}): {resp.text[:300]}")
    body = resp.json()
    try:
        text = body["candidates"][0]["content"]["parts"][0]["text"]
        return _extract_json(text)
    except (KeyError, IndexError, json.JSONDecodeError, TypeError) as exc:
        raise RuntimeError(f"Could not parse Gemini response: {body}") from exc


def classify_batch(api_key: str, articles: list[RawArticle]) -> list[ClassifiedLabels]:
    """Label several articles in one Gemini call (order preserved)."""
    if not articles:
        return []

    lines = []
    for idx, art in enumerate(articles):
        lines.append(
            f'{idx}. source={art.source!r} title={art.title!r} summary={art.description[:400]!r}'
        )
    prompt = f"""You label news for Prism, an app that shows bias and topic tags.

Return ONLY valid JSON:
{{
  "items": [
    {{
      "index": 0,
      "bias": one of {list(BIASES)},
      "scope": one of {list(SCOPES)},
      "topic": one of {list(INTERESTS)},
      "interests": 1 to 3 tags from {list(INTERESTS)}
    }}
  ]
}}

Rules:
- One object per article index below (0..{len(articles) - 1}).
- bias: outlet lean; use Neutral for wire services / straight news.
- scope: local = city/region; national = one country; international = multi-country/global.
- topic: single best primary topic from the allowed list.
- interests: only from the allowed list.

Articles:
{chr(10).join(lines)}
"""

    data = _gemini_json(api_key, prompt)
    if isinstance(data, list):
        items = data
    else:
        items = data.get("items") or []
    by_index: dict[int, dict] = {}
    for item in items:
        if isinstance(item, dict) and isinstance(item.get("index"), int):
            by_index[item["index"]] = item

    results: list[ClassifiedLabels] = []
    for idx, art in enumerate(articles):
        if idx in by_index:
            results.append(_clamp_labels(by_index[idx], art.source))
        else:
            results.append(_fallback(art))
    return results


def classify_many(api_key: str, articles: list[RawArticle]) -> list[ClassifiedLabels]:
    """Classify all articles in batches, with pauses for free-tier rate limits."""
    out: list[ClassifiedLabels] = []
    for start in range(0, len(articles), BATCH_SIZE):
        chunk = articles[start : start + BATCH_SIZE]
        print(f"  classifying batch {start + 1}-{start + len(chunk)} of {len(articles)}...")
        try:
            out.extend(classify_batch(api_key, chunk))
        except Exception as exc:  # noqa: BLE001
            print(f"  ! batch classify failed: {exc}")
            print("    using source-bias fallback for this batch")
            out.extend(_fallback(a) for a in chunk)
        if start + BATCH_SIZE < len(articles):
            time.sleep(BATCH_PAUSE_SEC)
    return out
