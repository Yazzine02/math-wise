from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
from sympy import Eq, Symbol, expand, simplify, solve, sympify
from sympy.parsing.sympy_parser import (
    parse_expr,
    standard_transformations,
    implicit_multiplication_application,
    convert_xor,
)
import exercise_templates
import requests
import os
import json
import logging

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


class GenerateExercisesRequest(BaseModel):
    node_code: str
    count: int = 5


class GeneratedExercise(BaseModel):
    question_text: str
    correct_answer: str       # SymPy-verified canonical form, NOT the LLM's claim
    difficulty_level: int


class GenerateExercisesResponse(BaseModel):
    exercises: list[GeneratedExercise]
    rejected_count: int       # how many LLM candidates failed validation
    used_fallback: bool       # true ⇒ deterministic templates produced these

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


# ====================================================================
# PHASE 8 — Exercise generation
# ====================================================================
#
# Spring Boot's ExerciseGenerationService calls /generate-exercises in
# the background whenever a knowledge node's pool drops below the
# minimum size. Two tiers, in order:
#
#   1. Ask the LLM for `count` candidates. Each candidate is verified
#      with SymPy — the LLM might produce wrong "expected" answers and
#      we trust SymPy's evaluation, not the LLM's claim.
#   2. If 0 LLM candidates pass verification (LLM offline, malformed
#      JSON, or all answers wrong), fall back to deterministic
#      templates from exercise_templates.py. Always correct by
#      construction.
#
# The student request that triggered generation never blocks on this —
# Spring Boot fires it as an async event.

_logger = logging.getLogger("uvicorn.error")


GENERATION_PROMPTS: dict[str, str] = {
    "ARITH_ADDITION": """Generate {count} arithmetic addition problems.
Each problem adds two integers between 10 and 999.

Return a JSON array. Each entry must have these fields:
  "question_text": natural-language form, e.g. "What is 47 + 38?"
  "math_expression": parseable form, e.g. "47 + 38"
  "expected_answer": your computed answer as an integer string, e.g. "85"
  "difficulty_level": 1 if both numbers two-digit, else 2

Example:
[
  {{"question_text": "What is 47 + 38?", "math_expression": "47 + 38", "expected_answer": "85", "difficulty_level": 1}},
  {{"question_text": "Calculate 256 + 744.", "math_expression": "256 + 744", "expected_answer": "1000", "difficulty_level": 2}}
]

Return ONLY the JSON array. No commentary, no markdown, no explanation.""",

    "ARITH_SUBTRACTION": """Generate {count} arithmetic subtraction problems with positive results.
Each problem subtracts two integers; the minuend MUST be larger than the subtrahend.

Return a JSON array. Each entry:
  "question_text": e.g. "What is 100 - 37?"
  "math_expression": e.g. "100 - 37"
  "expected_answer": integer string, e.g. "63"
  "difficulty_level": 1 or 2 based on size

Example: [{{"question_text": "What is 100 - 37?", "math_expression": "100 - 37", "expected_answer": "63", "difficulty_level": 1}}]

Return ONLY the JSON array.""",

    "ARITH_MULTIPLICATION": """Generate {count} arithmetic multiplication problems.
Multiply two integers, each between 2 and 25.

Return a JSON array. Each entry:
  "question_text": e.g. "What is 7 × 8?"  (note: use the × character)
  "math_expression": e.g. "7 * 8"  (use Python-style *)
  "expected_answer": integer string, e.g. "56"
  "difficulty_level": 1 if both single-digit, else 2

Example: [{{"question_text": "What is 7 × 8?", "math_expression": "7 * 8", "expected_answer": "56", "difficulty_level": 1}}]

Return ONLY the JSON array.""",

    "ARITH_DIVISION": """Generate {count} division problems with EXACT integer quotients.
For each problem, first pick a quotient q (2-20) and a divisor d (2-12), then form the dividend a = q × d. The problem is then "a ÷ d", which must divide evenly.

Return a JSON array. Each entry:
  "question_text": e.g. "What is 84 ÷ 7?"
  "math_expression": e.g. "84 / 7"
  "expected_answer": integer string, e.g. "12"
  "difficulty_level": 1 if d ≤ 9, else 2

Example: [{{"question_text": "What is 84 ÷ 7?", "math_expression": "84 / 7", "expected_answer": "12", "difficulty_level": 1}}]

Return ONLY the JSON array.""",

    "FRACTIONS_SIMPLIFY": """Generate {count} fraction simplification problems.
Pick two integers n and d (2 ≤ d ≤ 24, 1 ≤ n < d) that share a common factor greater than 1. The student simplifies n/d.

Return a JSON array. Each entry:
  "question_text": e.g. "Simplify 8/12."
  "math_expression": the original fraction as parseable form, e.g. "8/12"
  "expected_answer": the reduced fraction, e.g. "2/3"
  "difficulty_level": 3

Example: [{{"question_text": "Simplify 8/12.", "math_expression": "8/12", "expected_answer": "2/3", "difficulty_level": 3}}]

Return ONLY the JSON array.""",

    "FRACTIONS_ADD_SUB": """Generate {count} fraction addition or subtraction problems with simple denominators (2-8).
For subtraction, ensure the result is non-negative.

Return a JSON array. Each entry:
  "question_text": e.g. "What is 1/4 + 1/4?"
  "math_expression": parseable form using Python /, e.g. "1/4 + 1/4"
  "expected_answer": reduced fraction or integer string, e.g. "1/2"
  "difficulty_level": 3

Example: [{{"question_text": "What is 1/4 + 1/4?", "math_expression": "1/4 + 1/4", "expected_answer": "1/2", "difficulty_level": 3}}]

Return ONLY the JSON array.""",

    "ALGEBRA_LINEAR": """Generate {count} linear equations in one variable with integer solutions.
Form: ax + b = c where a (2-9), x (the integer solution, 2-20), b (-10 to 10) are integers, and c = a*x + b.

Return a JSON array. Each entry:
  "question_text": e.g. "Solve for x: 3x - 4 = 11."
  "math_expression": SymPy Eq form using Python operators, e.g. "Eq(3*x - 4, 11)"
  "expected_answer": value of x as a string, e.g. "5"
  "difficulty_level": 4

Example: [{{"question_text": "Solve for x: 3x - 4 = 11.", "math_expression": "Eq(3*x - 4, 11)", "expected_answer": "5", "difficulty_level": 4}}]

Return ONLY the JSON array.""",

    "ALGEBRA_FACTORIZE": """Generate {count} quadratic factorization problems.
Each polynomial is x^2 + bx + c that factors cleanly into (x + p)(x + q) with integer p, q in range -5 to 5.

Return a JSON array. Each entry:
  "question_text": e.g. "Factorize: x^2 + 5x + 6."
  "math_expression": Python form, e.g. "x**2 + 5*x + 6"
  "expected_answer": factored form, e.g. "(x + 2)*(x + 3)"
  "difficulty_level": 5

Example: [{{"question_text": "Factorize: x^2 + 5x + 6.", "math_expression": "x**2 + 5*x + 6", "expected_answer": "(x + 2)*(x + 3)", "difficulty_level": 5}}]

Return ONLY the JSON array.""",
}


def _verify_arithmetic(candidate: dict, *, require_integer: bool = False) -> dict | None:
    """For arithmetic + fractions: math_expression must simplify to expected_answer.
    If require_integer is True (used for ARITH_DIVISION), the canonical answer
    must also be a whole number — otherwise we'd accept "1 ÷ 3 = 1/3" type
    questions that wouldn't fit the node's pedagogical scope.
    """
    try:
        expr = parse_expr(
            candidate["math_expression"], transformations=_SYMPY_TRANSFORMATIONS
        )
        canonical = simplify(expr)
        if require_integer and not canonical.is_integer:
            return None
        claimed = parse_expr(
            candidate["expected_answer"], transformations=_SYMPY_TRANSFORMATIONS
        )
        if simplify(canonical - claimed) != 0:
            return None
        return {
            "question_text": candidate["question_text"],
            "correct_answer": str(canonical),
            "difficulty_level": int(candidate.get("difficulty_level", 1)),
        }
    except Exception as ex:
        _logger.debug("arithmetic verify failed: %s", ex)
        return None


def _verify_linear(candidate: dict) -> dict | None:
    """For ALGEBRA_LINEAR: solve the equation, must have exactly one integer solution
    matching the LLM's claimed value."""
    try:
        x = Symbol("x")
        eq = sympify(candidate["math_expression"])
        if not isinstance(eq, Eq):
            return None
        sols = solve(eq, x)
        if len(sols) != 1:
            return None
        canonical = sols[0]
        claimed = sympify(candidate["expected_answer"])
        if simplify(canonical - claimed) != 0:
            return None
        return {
            "question_text": candidate["question_text"],
            "correct_answer": str(canonical),
            "difficulty_level": int(candidate.get("difficulty_level", 4)),
        }
    except Exception as ex:
        _logger.debug("linear verify failed: %s", ex)
        return None


def _verify_factorize(candidate: dict) -> dict | None:
    """For ALGEBRA_FACTORIZE: the LLM's factored expression must expand back to
    the polynomial given in math_expression."""
    try:
        polynomial = parse_expr(
            candidate["math_expression"], transformations=_SYMPY_TRANSFORMATIONS
        )
        claimed_factored = parse_expr(
            candidate["expected_answer"], transformations=_SYMPY_TRANSFORMATIONS
        )
        if expand(claimed_factored - polynomial) != 0:
            return None
        return {
            "question_text": candidate["question_text"],
            "correct_answer": str(claimed_factored),
            "difficulty_level": int(candidate.get("difficulty_level", 5)),
        }
    except Exception as ex:
        _logger.debug("factorize verify failed: %s", ex)
        return None


# Dispatch table: node_code → verifier function.
_VERIFIERS: dict[str, callable] = {
    "ARITH_ADDITION":       lambda c: _verify_arithmetic(c),
    "ARITH_SUBTRACTION":    lambda c: _verify_arithmetic(c),
    "ARITH_MULTIPLICATION": lambda c: _verify_arithmetic(c),
    "ARITH_DIVISION":       lambda c: _verify_arithmetic(c, require_integer=True),
    "FRACTIONS_SIMPLIFY":   lambda c: _verify_arithmetic(c),
    "FRACTIONS_ADD_SUB":    lambda c: _verify_arithmetic(c),
    "ALGEBRA_LINEAR":       _verify_linear,
    "ALGEBRA_FACTORIZE":    _verify_factorize,
}


def _try_llm_generation(node_code: str, count: int) -> tuple[list[dict], int]:
    """Returns (validated_candidates, rejected_count). On any LLM-level
    failure (HTTP error, unparseable JSON, wrong shape) returns ([], 0) and
    lets the caller fall back to templates."""
    prompt_template = GENERATION_PROMPTS.get(node_code)
    if prompt_template is None:
        return [], 0
    prompt = prompt_template.format(count=count)

    try:
        ai_engine = get_ai_adapter()
        raw = ai_engine.evaluate_student_error(prompt)
    except Exception as ex:
        _logger.warning("LLM generation call failed for %s: %s", node_code, ex)
        return [], 0

    # Ollama with format=json returns whatever JSON shape we asked for —
    # ideally a list, but the LLM may wrap it in an object. Normalise.
    if isinstance(raw, dict):
        # Try common wrapping keys before giving up.
        for key in ("exercises", "problems", "items", "data"):
            if key in raw and isinstance(raw[key], list):
                raw = raw[key]
                break
        else:
            _logger.warning("LLM returned a dict, not a list, for %s: %s", node_code, list(raw)[:5])
            return [], 0
    if not isinstance(raw, list):
        return [], 0

    verifier = _VERIFIERS.get(node_code)
    if verifier is None:
        return [], 0

    accepted: list[dict] = []
    rejected = 0
    for candidate in raw:
        if not isinstance(candidate, dict):
            rejected += 1
            continue
        verified = verifier(candidate)
        if verified is None:
            rejected += 1
        else:
            accepted.append(verified)
    return accepted, rejected


@app.post("/generate-exercises", response_model=GenerateExercisesResponse)
def generate_exercises(request: GenerateExercisesRequest) -> GenerateExercisesResponse:
    """
    Two-tier generation pipeline:

      1. LLM produces `count` candidates; each is verified with SymPy.
         The stored answer is SymPy's canonical form — the LLM's
         "expected_answer" is only used as a sanity check.
      2. If 0 LLM candidates pass verification, fall back to the
         deterministic templates. Always correct by construction.

    Spring Boot fires this asynchronously when a node's exercise pool
    drops below its minimum size. The student's user-facing request that
    triggered the top-up never waits on this endpoint.
    """
    if request.node_code not in _VERIFIERS:
        raise HTTPException(
            status_code=400, detail=f"Unknown node_code: {request.node_code}"
        )
    if request.count < 1 or request.count > 20:
        raise HTTPException(
            status_code=400, detail="count must be between 1 and 20"
        )

    accepted, rejected = _try_llm_generation(request.node_code, request.count)

    used_fallback = False
    if not accepted:
        used_fallback = True
        accepted = exercise_templates.generate_templates(request.node_code, request.count)
        _logger.info(
            "Falling back to deterministic templates for %s (rejected %d LLM candidates)",
            request.node_code, rejected,
        )

    return GenerateExercisesResponse(
        exercises=[GeneratedExercise(**a) for a in accepted],
        rejected_count=rejected,
        used_fallback=used_fallback,
    )