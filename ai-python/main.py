from fastapi import FastAPI
from pydantic import BaseModel
import requests

app = FastAPI(title="Math Wise AI Service")

# Define the structure of the data Spring Boot will send us
class MathEvaluationRequest(BaseModel):
    equation: str
    correct_answer: str
    student_answer: str

@app.get("/")
def read_root():
    return {"status": "AI Service is running"}

@app.post("/evaluate-error")
def evaluate_student_error(request: MathEvaluationRequest):
    # TODO: Build the system prompt using request.equation, etc.
    # TODO: Send the prompt to localhost:11434 (Ollama)
    # TODO: Parse the LLM response
    
    # Mock response for now to ensure routing works
    return {
        "weakness_node": "MOCK_NODE",
        "explanation": "This is a placeholder explanation until Ollama is connected."
    }