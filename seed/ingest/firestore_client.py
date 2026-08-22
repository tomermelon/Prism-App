"""Firestore Admin helpers for article upsert."""

from __future__ import annotations

import hashlib

import firebase_admin
from firebase_admin import credentials, firestore

from .news import RawArticle
from .classify import ClassifiedLabels


def init_firebase(credentials_path: str, project_id: str) -> firestore.Client:
    if not firebase_admin._apps:
        cred = credentials.Certificate(credentials_path)
        firebase_admin.initialize_app(cred, {"projectId": project_id})
    return firestore.client()


def existing_urls(db: firestore.Client) -> set[str]:
    urls: set[str] = set()
    for doc in db.collection("articles").stream():
        url = (doc.to_dict() or {}).get("url")
        if isinstance(url, str) and url:
            urls.add(url)
    return urls


def doc_id_for_url(url: str) -> str:
    return hashlib.sha256(url.encode("utf-8")).hexdigest()[:20]


def write_article(
    db: firestore.Client,
    raw: RawArticle,
    labels: ClassifiedLabels,
) -> str:
    doc_id = doc_id_for_url(raw.url)
    payload = {
        "title": raw.title,
        "description": raw.description,
        "body": raw.description,
        "source": raw.source,
        "topic": labels.topic,
        "bias": labels.bias,
        "scope": labels.scope,
        "interests": labels.interests,
        "url": raw.url,
        "createdAt": raw.published_at_ms,
    }
    db.collection("articles").document(doc_id).set(payload, merge=True)
    return doc_id
