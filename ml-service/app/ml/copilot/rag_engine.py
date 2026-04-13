"""RAG engine — embed query, retrieve context, generate LLM response.

Supports Ollama (local), Anthropic, and OpenAI as LLM backends.
"""

from __future__ import annotations

import logging
import os
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

import httpx

from app.config import MlSettings
from .vector_store import SearchResult, VectorStore

logger = logging.getLogger(__name__)

# ------------------------------------------------------------------
# System prompt for the copilot
# ------------------------------------------------------------------

SYSTEM_PROMPT = """\
You are Numera Copilot, an AI assistant for commercial lending analysts.
You help with financial spreading, covenant analysis, and document review.

Rules:
- Answer ONLY based on the provided context. If the answer is not in the context, say so.
- Cite your sources using [Source N] notation keyed to the context blocks.
- Be precise with numbers and financial terminology.
- When uncertain, indicate your confidence level.
- Never fabricate financial data.
"""


class LlmProvider(str, Enum):
    OLLAMA = "ollama"
    ANTHROPIC = "anthropic"
    OPENAI = "openai"


@dataclass
class Citation:
    """A source reference attached to a copilot answer."""

    source_id: str
    text: str
    collection: str
    score: float
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class CopilotResponse:
    """Structured response returned by the RAG engine."""

    answer: str
    citations: list[Citation]
    model: str
    provider: str
    latency_ms: int
    context_tokens: int = 0


class RagEngine:
    """Retrieval-Augmented Generation engine for Numera Copilot."""

    def __init__(
        self,
        vector_store: VectorStore | None = None,
        settings: MlSettings | None = None,
    ) -> None:
        self._settings = settings or MlSettings()
        self._store = vector_store or VectorStore(self._settings)
        self._provider = LlmProvider(os.getenv("COPILOT_LLM_PROVIDER", "ollama"))
        self._ollama_url = os.getenv("COPILOT_OLLAMA_URL", "http://localhost:11434")
        self._ollama_model = os.getenv("COPILOT_OLLAMA_MODEL", "llama3")
        self._anthropic_key = os.getenv("ANTHROPIC_API_KEY", "")
        self._anthropic_model = os.getenv("COPILOT_ANTHROPIC_MODEL", "claude-sonnet-4-20250514")
        self._openai_key = os.getenv("OPENAI_API_KEY", "")
        self._openai_model = os.getenv("COPILOT_OPENAI_MODEL", "gpt-4o-mini")

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def query(
        self,
        question: str,
        *,
        collections: list[str] | None = None,
        top_k: int = 5,
        customer_id: str | None = None,
    ) -> CopilotResponse:
        """Run full RAG: retrieve → build prompt → generate → return."""
        start = time.monotonic()

        # 1. Retrieve relevant context
        where_filter: dict[str, Any] | None = None
        if customer_id:
            where_filter = {"customer_id": customer_id}

        hits = self._store.search(
            query=question,
            collections=collections,
            top_k=top_k,
            where=where_filter,
        )

        # 2. Build prompt with context
        prompt = self._build_prompt(question, hits)

        # 3. Generate LLM response
        answer, model_name = self._generate(prompt)

        # 4. Build citations
        citations = [
            Citation(
                source_id=hit.id,
                text=hit.text[:200],
                collection=hit.metadata.get("collection", "unknown"),
                score=hit.score,
                metadata=hit.metadata,
            )
            for hit in hits
        ]

        elapsed_ms = int((time.monotonic() - start) * 1000)
        return CopilotResponse(
            answer=answer,
            citations=citations,
            model=model_name,
            provider=self._provider.value,
            latency_ms=elapsed_ms,
            context_tokens=len(prompt.split()),
        )

    # ------------------------------------------------------------------
    # Prompt construction
    # ------------------------------------------------------------------

    def _build_prompt(self, question: str, hits: list[SearchResult]) -> str:
        context_blocks: list[str] = []
        for idx, hit in enumerate(hits, 1):
            block = (
                f"[Source {idx}] (collection={hit.metadata.get('collection', '?')}, "
                f"score={hit.score:.2f})\n{hit.text}"
            )
            context_blocks.append(block)

        context_section = "\n\n".join(context_blocks) if context_blocks else "(No relevant context found.)"

        return (
            f"{SYSTEM_PROMPT}\n\n"
            f"## Context\n{context_section}\n\n"
            f"## Question\n{question}"
        )

    # ------------------------------------------------------------------
    # LLM generation (provider dispatch)
    # ------------------------------------------------------------------

    def _generate(self, prompt: str) -> tuple[str, str]:
        """Dispatch to configured LLM provider and return (answer, model_name)."""
        try:
            if self._provider == LlmProvider.OLLAMA:
                return self._generate_ollama(prompt)
            elif self._provider == LlmProvider.ANTHROPIC:
                return self._generate_anthropic(prompt)
            elif self._provider == LlmProvider.OPENAI:
                return self._generate_openai(prompt)
            else:
                return self._generate_ollama(prompt)
        except Exception:
            logger.error("LLM generation failed (provider=%s)", self._provider.value, exc_info=True)
            return (
                "I'm sorry, I couldn't generate a response at this time. "
                "Please check the LLM service configuration.",
                f"{self._provider.value}/error",
            )

    def _generate_ollama(self, prompt: str) -> tuple[str, str]:
        with httpx.Client(timeout=120.0) as client:
            resp = client.post(
                f"{self._ollama_url}/api/generate",
                json={
                    "model": self._ollama_model,
                    "prompt": prompt,
                    "stream": False,
                    "options": {"temperature": 0.1, "num_predict": 1024},
                },
            )
            resp.raise_for_status()
            data = resp.json()
            return data.get("response", ""), f"ollama/{self._ollama_model}"

    def _generate_anthropic(self, prompt: str) -> tuple[str, str]:
        if not self._anthropic_key:
            raise ValueError("ANTHROPIC_API_KEY not configured")
        with httpx.Client(timeout=120.0) as client:
            resp = client.post(
                "https://api.anthropic.com/v1/messages",
                headers={
                    "x-api-key": self._anthropic_key,
                    "anthropic-version": "2023-06-01",
                    "content-type": "application/json",
                },
                json={
                    "model": self._anthropic_model,
                    "max_tokens": 1024,
                    "messages": [{"role": "user", "content": prompt}],
                    "system": SYSTEM_PROMPT,
                    "temperature": 0.1,
                },
            )
            resp.raise_for_status()
            data = resp.json()
            content_blocks = data.get("content", [])
            answer = "".join(b.get("text", "") for b in content_blocks if b.get("type") == "text")
            return answer, f"anthropic/{self._anthropic_model}"

    def _generate_openai(self, prompt: str) -> tuple[str, str]:
        if not self._openai_key:
            raise ValueError("OPENAI_API_KEY not configured")
        with httpx.Client(timeout=120.0) as client:
            resp = client.post(
                "https://api.openai.com/v1/chat/completions",
                headers={
                    "Authorization": f"Bearer {self._openai_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "model": self._openai_model,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": prompt},
                    ],
                    "temperature": 0.1,
                    "max_tokens": 1024,
                },
            )
            resp.raise_for_status()
            data = resp.json()
            choices = data.get("choices", [])
            answer = choices[0]["message"]["content"] if choices else ""
            return answer, f"openai/{self._openai_model}"
