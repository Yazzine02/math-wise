from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
from abc import ABC, abstractmethod
import requests
import os
import json

# Loading environment variables using python-dotenv
load_dotenv()

app = FastAPI(title="Math Wise AI Service")


# ----DATA MODELS----
class MathEvaluationRequest(BaseModel):
    equation: str
    correct_answer: str
    student_answer: str


# ----ADAPTER----
# All adapters have to implement the evaluate_student_error method
class AIEngineAdapter(ABC):
    @abstractmethod
    def evaluate(self, prompt: str) -> dict:
        """Send prompt to Ai engine and return its JSON output"""
        ...


class CloudAPIAdapter(AIEngineAdapter):
    """Adapter for online cloud API"""

    def __init__(self):
        # Use os directly thanks to load_dotenv, provided by python-dotenv depandency
        self.api_key = os.getenv("CLOUD_API_KEY")
        self.url = os.getenv("CLOUD_API_URI")

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
            response = requests.post(self.url, headers=headers, json=payload)
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
            response = requests.post(self.url, json=payload)
            data = response.json()
            return json.loads(data["response"])
        except Exception as e:
            raise HTTPException(
                status_code=500, detail="Ensure Ollama is running locally. " + str(e)
            )


# ----FACTORY----
def get_ai_adapter() -> AIEngineAdapter:
    mode = os.getenv("AI_MODE")
    if not mode:
        raise RuntimeError(
           "AI_MODE env var is required. Set it to 'local' or 'cloud' "
           "(see ai-python/.env.example)."
       )
    mode = mode.strip().lower()
    if mode == "local":
        return LocalModelAdapter()
    return CloudAPIAdapter()


# ----FAST API ENDPOINTS----
@app.post("/evaluate-error")
def evaluate_student_error(request: MathEvaluationRequest):
    # 1. Construct the strict prompt
    system_prompt = f"""
    You are an expert math tutor. Analyze the student's incorrect answer.
    Equation: {request.equation}
    Correct Answer: {request.correct_answer}
    Student Answer: {request.student_answer}
    
    Return ONLY a JSON object with two keys: "weakness_node" (string) and "explanation" (string).
    """

    # 2. Get the active adapter (Cloud or Local based on .env)
    ai_engine = get_ai_adapter()

    # 3. Process the request
    try:
        result_json = ai_engine.evaluate(system_prompt)
        return result_json
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
