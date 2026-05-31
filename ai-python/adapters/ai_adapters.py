"""
AI engine adapters — selects between local Ollama and a cloud API based on
the AI_MODE env var. Lifted out of main.py during the Phase 11 (RAG) cherry
pick so the diagnostician + generation paths share one hardened
implementation.

The abstract method name `evaluate_student_error` is kept verbatim from the
pre-modular adapter so the existing `_try_llm_generation` path in main.py
keeps working without changes.
"""

from abc import ABC, abstractmethod
import json
import logging
import os

import requests
from dotenv import load_dotenv
from fastapi import HTTPException

load_dotenv()

_logger = logging.getLogger("uvicorn.error")


class AIEngineAdapter(ABC):
    """Contract every adapter must satisfy. ABC enforces the implementation
    instead of relying on duck-typing — instantiating a subclass that forgets
    `evaluate_student_error` fails at import time, not at first request."""

    @abstractmethod
    def evaluate_student_error(self, prompt: str) -> dict:
        """Send `prompt` to the AI engine and return the parsed JSON dict."""
        ...


class CloudAPIAdapter(AIEngineAdapter):
    def __init__(self) -> None:
        self.api_key = os.getenv("CLOUD_API_KEY")
        self.url = os.getenv("CLOUD_API_URI")
        if not self.api_key:
            raise RuntimeError("CLOUD_API_KEY is not set (.env)")
        if not self.url:
            raise RuntimeError("CLOUD_API_URI is not set (.env)")

    def evaluate_student_error(self, prompt: str) -> dict:
        _logger.info("AI call → Cloud API")
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": "fast-cloud-model",
            "messages": [{"role": "user", "content": prompt}],
        }
        try:
            response = requests.post(self.url, headers=headers, json=payload, timeout=60)
            data = response.json()
            return json.loads(data["choices"][0]["message"]["content"])
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Cloud API failed: {e}")


class LocalModelAdapter(AIEngineAdapter):
    def __init__(self) -> None:
        self.url = os.getenv("MODEL_URL")
        self.model = os.getenv("MODEL_NAME")
        if not self.url:
            raise RuntimeError("MODEL_URL is not set (.env)")
        if not self.model:
            raise RuntimeError("MODEL_NAME is not set (.env)")

    def evaluate_student_error(self, prompt: str) -> dict:
        _logger.info("AI call → local Ollama (%s)", self.model)
        payload = {
            "model": self.model,
            "prompt": prompt,
            "stream": False,
            "format": "json",
        }
        try:
            response = requests.post(f"{self.url}/api/generate", json=payload, timeout=120)
            data = response.json()
            return json.loads(data["response"])
        except Exception as e:
            raise HTTPException(
                status_code=500,
                detail=f"Ensure Ollama is running locally. {e}",
            )


def get_ai_adapter() -> AIEngineAdapter:
    mode = (os.getenv("AI_MODE") or "").strip().lower()
    if not mode:
        raise RuntimeError(
            "AI_MODE env var is required. Set it to 'local' or 'cloud' "
            "(see ai-python/.env.example)."
        )
    if mode not in {"local", "cloud"}:
        raise RuntimeError(f"AI_MODE must be 'local' or 'cloud', got {mode!r}")
    return LocalModelAdapter() if mode == "local" else CloudAPIAdapter()
