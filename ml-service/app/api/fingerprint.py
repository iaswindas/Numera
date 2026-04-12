"""POST /api/ml/fingerprint/match — Structural template matching."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np
from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.config import settings

router = APIRouter()

_template_cache: dict[str, list[dict]] = {}


class FingerprintPayload(BaseModel):
    hash: str
    embedding: list[float]
    page_idx: int
    node_count: int
    created_at: str | None = None
    table_ids: list[str] = Field(default_factory=list)


class TemplateFingerprintPayload(BaseModel):
    template_id: str
    fingerprints: list[FingerprintPayload]


class FingerprintMatchRequest(BaseModel):
    document_id: str
    candidate_fingerprints: list[FingerprintPayload] = Field(default_factory=list)
    template_ids: list[str] = Field(default_factory=list)
    templates: list[TemplateFingerprintPayload] = Field(default_factory=list)


class FingerprintMatchResult(BaseModel):
    template_id: str
    similarity: float
    hash_similarity: float
    cosine_similarity: float
    matched_page_idx: int | None = None


class FingerprintMatchResponse(BaseModel):
    document_id: str
    matched: bool
    best_match: FingerprintMatchResult | None
    matches: list[FingerprintMatchResult]


@router.post("/match", response_model=FingerprintMatchResponse)
async def match_fingerprint(request: FingerprintMatchRequest):
    candidates = [
        _normalize_payload(fingerprint)
        for fingerprint in request.candidate_fingerprints
    ]
    if not candidates:
        return FingerprintMatchResponse(
            document_id=request.document_id,
            matched=False,
            best_match=None,
            matches=[],
        )

    template_groups = list(request.templates)
    template_ids = list(request.template_ids)
    if not template_groups and not template_ids:
        template_ids = _discover_template_ids()
    for template_id in template_ids:
        template_groups.append(
            TemplateFingerprintPayload(
                template_id=template_id,
                fingerprints=[FingerprintPayload(**payload) for payload in _load_template_fingerprints(template_id)],
            )
        )

    results: list[FingerprintMatchResult] = []
    for template in template_groups:
        best_match = _match_template(candidates, template)
        if best_match is not None:
            results.append(best_match)

    results.sort(key=lambda result: result.similarity, reverse=True)
    best = results[0] if results else None
    matched = best is not None and best.similarity >= settings.fingerprint_similarity_threshold

    return FingerprintMatchResponse(
        document_id=request.document_id,
        matched=matched,
        best_match=best,
        matches=results,
    )


def _match_template(
    candidates: list[FingerprintPayload],
    template: TemplateFingerprintPayload,
) -> FingerprintMatchResult | None:
    best: FingerprintMatchResult | None = None

    for candidate in candidates:
        candidate_vector = _normalize_vector(candidate.embedding)
        for fingerprint in template.fingerprints:
            template_vector = _normalize_vector(fingerprint.embedding)
            hash_similarity = _hash_similarity(candidate.hash, fingerprint.hash)
            cosine_similarity = _cosine_similarity(candidate_vector, template_vector)
            similarity = round(max(0.0, min(1.0, 0.35 * hash_similarity + 0.65 * cosine_similarity)), 4)

            result = FingerprintMatchResult(
                template_id=template.template_id,
                similarity=similarity,
                hash_similarity=round(hash_similarity, 4),
                cosine_similarity=round(cosine_similarity, 4),
                matched_page_idx=candidate.page_idx,
            )

            if best is None or result.similarity > best.similarity:
                best = result

    return best


def _normalize_payload(payload: FingerprintPayload) -> FingerprintPayload:
    normalized = _normalize_vector(payload.embedding)
    return FingerprintPayload(
        hash=payload.hash,
        embedding=normalized.tolist(),
        page_idx=payload.page_idx,
        node_count=payload.node_count,
        created_at=payload.created_at,
        table_ids=payload.table_ids,
    )


def _load_template_fingerprints(template_id: str) -> list[dict]:
    if template_id in _template_cache:
        return _template_cache[template_id]

    template = _load_template_json(template_id)
    if template is None:
        _template_cache[template_id] = []
        return []

    fingerprints = []
    for page_idx, section in enumerate(template.get("sections", [])):
        embedding = _section_embedding(section)
        fingerprints.append(
            {
                "hash": _hash_embedding(embedding),
                "embedding": embedding.tolist(),
                "page_idx": page_idx,
                "node_count": len(section.get("items", [])),
                "created_at": "template",
                "table_ids": [],
            }
        )

    _template_cache[template_id] = fingerprints
    return fingerprints


def _load_template_json(template_id: str) -> dict | None:
    search_dirs = [
        Path("/app/data/model_templates"),
        Path("data/model_templates"),
        Path(__file__).parent.parent.parent.parent / "data" / "model_templates",
    ]

    for directory in search_dirs:
        candidate = directory / f"{template_id.replace('-', '_')}.json"
        if candidate.exists():
            return json.loads(candidate.read_text())

    for directory in search_dirs:
        for candidate in directory.glob("*.json"):
            try:
                data = json.loads(candidate.read_text())
            except Exception:
                continue
            if data.get("_meta", {}).get("id") == template_id:
                return data

    return None


def _discover_template_ids() -> list[str]:
    search_dirs = [
        Path("/app/data/model_templates"),
        Path("data/model_templates"),
        Path(__file__).parent.parent.parent.parent / "data" / "model_templates",
    ]

    template_ids: list[str] = []
    for directory in search_dirs:
        if not directory.exists():
            continue
        for candidate in directory.glob("*.json"):
            try:
                data = json.loads(candidate.read_text())
            except Exception:
                continue
            template_id = data.get("_meta", {}).get("id")
            if template_id and template_id not in template_ids:
                template_ids.append(template_id)
    return template_ids


def _section_embedding(section: dict, embedding_dim: int = 256) -> np.ndarray:
    vector = np.zeros(embedding_dim, dtype=np.float32)
    zone = section.get("zone_type", "OTHER")
    items = section.get("items", [])

    for index, item in enumerate(items):
        label = item.get("label", "")
        indent = item.get("indent", 0)
        item_type = item.get("type", "INPUT")
        is_total = item.get("is_total", False)
        text = f"{zone}|{item_type}|{indent}|{label.lower().strip()}"
        token_vector = _hash_text(text, embedding_dim)
        position_weight = 1.0 - (index / max(len(items), 1)) * 0.35
        vector += token_vector * position_weight
        if is_total:
            vector += _hash_text("__total__", embedding_dim) * 0.15

    return _normalize_vector(vector)


def _hash_embedding(embedding: np.ndarray, hash_bits: int = 256) -> str:
    rng = np.random.default_rng(42)
    projections = rng.standard_normal((hash_bits, len(embedding)), dtype=np.float32)
    bits = embedding @ projections.T >= 0
    bit_string = "".join("1" if bit else "0" for bit in bits.tolist())
    return f"{int(bit_string, 2):0{hash_bits // 4}x}"


def _hash_text(text: str, embedding_dim: int) -> np.ndarray:
    vector = np.zeros(embedding_dim, dtype=np.float32)
    tokens = [token for token in text.replace("|", " ").split() if token]
    if not tokens:
        tokens = ["__empty__"]

    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % embedding_dim
        sign = 1.0 if digest[4] % 2 == 0 else -1.0
        vector[index] += sign

    return _normalize_vector(vector)


def _normalize_vector(values) -> np.ndarray:
    vector = np.asarray(values, dtype=np.float32)
    norm = float(np.linalg.norm(vector))
    if norm > 0:
        vector = vector / norm
    return vector


def _hash_similarity(hash_one: str, hash_two: str) -> float:
    if not hash_one or not hash_two:
        return 0.0

    bits_one = bin(int(hash_one, 16))[2:].zfill(len(hash_one) * 4)
    bits_two = bin(int(hash_two, 16))[2:].zfill(len(hash_two) * 4)
    distance = sum(bit_one != bit_two for bit_one, bit_two in zip(bits_one, bits_two, strict=False))
    return 1.0 - distance / max(len(bits_one), 1)


def _cosine_similarity(vector_one: np.ndarray, vector_two: np.ndarray) -> float:
    if vector_one.shape != vector_two.shape:
        vector_one, vector_two = _align_vectors(vector_one, vector_two)

    denominator = float(np.linalg.norm(vector_one) * np.linalg.norm(vector_two))
    if denominator <= 1e-8:
        return 0.0
    return float(np.dot(vector_one, vector_two) / denominator)


def _align_vectors(vector_one: np.ndarray, vector_two: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    target_dim = max(vector_one.shape[0], vector_two.shape[0])
    aligned_one = np.zeros(target_dim, dtype=np.float32)
    aligned_two = np.zeros(target_dim, dtype=np.float32)
    aligned_one[: vector_one.shape[0]] = vector_one
    aligned_two[: vector_two.shape[0]] = vector_two
    return aligned_one, aligned_two