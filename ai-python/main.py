from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
from sympy import simplify
from sympy.parsing.sympy_parser import (
    parse_expr,
    standard_transformations,
    implicit_multiplication_application,
    convert_xor,
)
import requests
import os
import json

#Loading environment variables using python-dotenv
load_dotenv()

app = FastAPI(title="Math Wise AI Service")

#----DATA MODELS----
class MathEvaluationRequest(BaseModel):
    equation: str
    correct_answer: str
    student_answer: str
    # Phase 7: topic + prereq chain. Optional so older Spring Boot revisions
    # without these fields still work — the prompt falls back to a generic
    # framing when they're absent.
    node_code: str | None = None
    prerequisite_codes: list[str] = []

class AnswerCheckRequest(BaseModel):
    correct_answer: str
    student_answer: str

class AnswerCheckResponse(BaseModel):
    is_correct: bool
    used_symbolic_check: bool  # False ⇒ we fell back to string equality

#----ADAPTER----
# All adapters have to implement the evaluate_student_error method
class AIEngineAdapter:
    def evaluate_student_error(self, prompt:str)-> dict:
        raise NotImplementedError("Subclasses must implement the evaluate_student_error method.")

class CloudAPIAdapter(AIEngineAdapter):
    """Adapter for online cloud API"""
    def __init__(self):
        # Use os directly thanks to load_dotenv, provided by python-dotenv depandency
        self.api_key=os.getenv("CLOUD_API_KEY")
        self.url=os.getenv("CLOUD_API_URI")

    def evaluate_student_error(self, prompt:str)->dict:
        print("Evaluating using your Cloud API")
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }
        payload = {
            "model": "fast-cloud-model",
            "messages": [{"role": "user", "content": prompt}]
        }
        try:
            response=requests.post(self.url, headers=headers, json=payload)
            data = response.json()
            return json.loads(data['choices'][0]['message']['content'])
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Cloud API failed: {str(e)}")
        
class LocalModelAdapter(AIEngineAdapter):
    def __init__(self):
        self.url=os.getenv("MODEL_URL")
        self.model=os.getenv("MODEL_NAME")
    
    def evaluate_student_error(self, prompt: str) -> dict:
        print("Evaluating using LOCAL OLLAMA MODEL...")
        payload = {
            "model": self.model,
            "prompt": prompt,
            "stream": False,
            "format": "json"
        }
        try:
            response = requests.post(f"{self.url}/api/generate", json=payload)
            data = response.json()
            return json.loads(data['response'])
        except Exception as e:
            raise HTTPException(status_code=500, detail="Ensure Ollama is running locally. " + str(e))

#----FACTORY----
def get_ai_adapter()->AIEngineAdapter:
    mode=os.getenv("AI_MODE").lower()
    if mode=="local":
        return LocalModelAdapter()
    return CloudAPIAdapter()

#----FAST API ENDPOINTS----
# Canonical knowledge-node codes, mirroring the seeded data in
# backend-springboot/.../config/DataSeeder.java. We embed the list directly in
# the prompt so the LLM picks one of these strings verbatim instead of
# inventing a free-form title like "Multiplication" (which downstream services
# cannot look up).
VALID_WEAKNESS_NODES = [
    "ARITH_ADDITION",
    "ARITH_SUBTRACTION",
    "ARITH_MULTIPLICATION",
    "ARITH_DIVISION",
    "FRACTIONS_SIMPLIFY",
    "FRACTIONS_ADD_SUB",
    "ALGEBRA_LINEAR",
    "ALGEBRA_FACTORIZE",
]

@app.post("/evaluate-error")
def evaluate_student_error(request: MathEvaluationRequest):
    # 1. Construct the prompt
    nodes_block = "\n".join(f"- {code}" for code in VALID_WEAKNESS_NODES)
    topic_block = (
        f"Topic being tested: {request.node_code}"
        if request.node_code else "Topic being tested: (unspecified)"
    )
    prereqs_block = (
        "Formal prerequisites in the curriculum: " + ", ".join(request.prerequisite_codes)
        if request.prerequisite_codes
        else "Formal prerequisites in the curriculum: (none — this is a foundational topic)"
    )

    system_prompt = f"""
You are an expert math tutor analyzing why a student got an answer wrong.
Your job is to identify the SPECIFIC underlying mistake, not just the topic.

Question: {request.equation}
Correct answer: {request.correct_answer}
Student's answer: {request.student_answer}
{topic_block}
{prereqs_block}

REASON STEP BY STEP. Mentally reconstruct the sequence of operations the
student most likely performed to reach their answer. Identify the FIRST
step where they went wrong.

The "weakness_node" should reflect the concept involved in THAT specific
erroneous step — which is very often a PREREQUISITE skill, not the topic
itself. Solving a linear equation requires addition, subtraction,
multiplication, and division; a slip in any of those is the real weakness
even though the question looked like an "algebra" question.

──── WORKED EXAMPLE ────
  Question: "Solve for x: 3x - 4 = 11"
  Correct answer: 5
  Student's answer: 3
  Topic: ALGEBRA_LINEAR

  Step-by-step reconstruction of what the student likely did:
    1. Started with: 3x - 4 = 11
    2. Added 4 to both sides:  3x = 15           (correct)
    3. Divided both sides by 3: x = 15 / 3       (correct setup)
    4. Computed: x = 3                           (WRONG: 15 / 3 = 5, not 3)

  The algebra moves (steps 2 and 3) were perfect. The slip is in step 4,
  a pure arithmetic-division error.
  → weakness_node: ARITH_DIVISION

──── INSTRUCTIONS ────
The "weakness_node" MUST be EXACTLY ONE of these codes, copied verbatim
(uppercase, with underscores). Do NOT translate, paraphrase, or use the
human-readable name:
{nodes_block}

Prefer a prerequisite code over the topic code itself when the error is
purely computational. Only attribute the weakness to the topic itself if
the student misunderstood the *method* (e.g. didn't isolate x correctly,
didn't apply the distributive property), not the arithmetic.

Return ONLY a JSON object with two keys:
- "weakness_node": (string) one of the codes above, exactly as written
- "explanation":   (string) a brief, friendly tutor explanation that
                   names the specific arithmetic mistake

Do not include any text outside the JSON.
"""
    
    # 2. Get the active adapter (Cloud or Local based on .env)
    ai_engine = get_ai_adapter()
    
    # 3. Process the request
    try:
        result_json = ai_engine.evaluate_student_error(system_prompt)
        return result_json
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ----SYMBOLIC ANSWER CHECK----
# SymPy parser transformations: lets us accept "2x" (implicit *), "(x+2)(x+3)"
# (juxtaposition multiplication), and "x^2" (caret as power) — the natural ways
# a student types math, not the Python literal forms.
_SYMPY_TRANSFORMATIONS = standard_transformations + (
    implicit_multiplication_application,
    convert_xor,
)


def _answers_equivalent(correct: str, student: str) -> tuple[bool, bool]:
    """
    Returns (is_equivalent, used_symbolic_check).

    Symbolic path: parse both sides into SymPy expressions and check whether
    `simplify(correct - student) == 0`. This makes the following pairs
    equivalent (none of which string equality catches):
        "2/3" ⇔ "4/6"
        "0.5" ⇔ "1/2"
        "(x+2)(x+3)" ⇔ "(x+3)(x+2)" ⇔ "x^2 + 5x + 6"
        "85"  ⇔ "85.0"

    If parsing either side fails (student typed a word, a unit, a sentence,
    etc.) we fall back to case-insensitive trimmed string equality so we never
    crash on non-mathematical input. The `used_symbolic_check` flag in the
    response tells the caller which path produced the verdict.
    """
    a = (correct or "").strip()
    b = (student or "").strip()
    if not a or not b:
        return (False, False)

    # Lowercase before parsing so "X" and "x" are treated the same. Safe for
    # this domain because all seeded exercises use lowercase identifiers.
    a_norm = a.lower()
    b_norm = b.lower()

    try:
        expr_a = parse_expr(a_norm, transformations=_SYMPY_TRANSFORMATIONS)
        expr_b = parse_expr(b_norm, transformations=_SYMPY_TRANSFORMATIONS)
        return (simplify(expr_a - expr_b) == 0, True)
    except Exception:
        # Unparseable on at least one side — fall back to plain text compare.
        return (a_norm == b_norm, False)


@app.post("/check-answer", response_model=AnswerCheckResponse)
def check_answer(request: AnswerCheckRequest) -> AnswerCheckResponse:
    """
    Fast (sub-50ms) authoritative correctness check. Spring Boot calls this
    BEFORE the LLM diagnosis endpoint, so correct answers skip the LLM
    entirely (saving 5–30s per request) and incorrect answers can still be
    routed through Llama for an explanation.
    """
    is_correct, used_symbolic = _answers_equivalent(
        request.correct_answer, request.student_answer
    )
    return AnswerCheckResponse(
        is_correct=is_correct,
        used_symbolic_check=used_symbolic,
    )