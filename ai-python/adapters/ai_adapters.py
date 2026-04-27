from abc import ABC, abstractmethod
import requests
import os
import json

from dotenv import load_dotenv
from fastapi import HTTPException

load_dotenv()


# ----ADAPTER----
# All adapters have to implement the evaluate method
class AIEngineAdapter(ABC):
    @abstractmethod
    def evaluate(self, prompt: str) -> dict:
        """Send prompt to AI engine and return its JSON output"""
        ...


class CloudAPIAdapter(AIEngineAdapter):
    """Adapter for online cloud API"""

    def __init__(self):
        self.api_key = os.getenv("CLOUD_API_KEY")
        self.url = os.getenv("CLOUD_API_URI")
        # Fail fast — ne pas envoyer "Bearer None" à l'API
        if not self.api_key:
            raise RuntimeError("CLOUD_API_KEY is not set in .env")
        if not self.url:
            raise RuntimeError("CLOUD_API_URI is not set in .env")

    def evaluate(self, prompt: str) -> dict:
        print("Evaluating using your Cloud API")
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }
        payload = {
            "model": "fast-cloud-model",
            "messages": [{"role": "user", "content": prompt}],
        }
        try:
            response = requests.post(
                self.url, headers=headers, json=payload, timeout=30
            )
            data = response.json()
            return json.loads(data["choices"][0]["message"]["content"])
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Cloud API failed: {str(e)}")


class LocalModelAdapter(AIEngineAdapter):
    def __init__(self):
        self.url = os.getenv("MODEL_URL")
        self.model = os.getenv("MODEL_NAME")

    def evaluate(self, prompt: str) -> dict:
        print("Evaluating using LOCAL OLLAMA MODEL...")
        payload = {
            "model": self.model,
            "prompt": prompt,
            "stream": False,
            "format": "json",
        }
        try:
            response = requests.post(self.url, json=payload, timeout=120)
            data = response.json()
            return json.loads(data["response"])
        except Exception as e:
            raise HTTPException(
                status_code=500,
                detail="Ensure Ollama is running locally. " + str(e)
            )


# ----FACTORY----
def get_ai_adapter() -> AIEngineAdapter:
    mode = os.getenv("AI_MODE", "").strip().lower()
    if not mode:
        raise RuntimeError(
            "AI_MODE env var is required. Set it to 'local' or 'cloud' "
            "(see ai-python/.env.example)."
        )
    if mode not in {"local", "cloud"}:
        raise RuntimeError(
            f"AI_MODE must be 'local' or 'cloud', got {mode!r}"
        )
    return LocalModelAdapter() if mode == "local" else CloudAPIAdapter()