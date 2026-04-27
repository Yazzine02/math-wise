from fastapi import FastAPI, HTTPException
from dotenv import load_dotenv

from models.shemas import EvaluationRequest, EvaluationResponse
from evaluation.pipeline import evaluate

load_dotenv()

app = FastAPI(title="Math Wise AI Service")


# ================================================================
# HEALTH CHECK
# ================================================================

@app.get("/health")
def health():
    """Vérifie que FastAPI tourne — appelé par Spring Boot au démarrage"""
    return {"status": "ok"}


# ================================================================
# ENDPOINT PRINCIPAL
# Appelé par Spring Boot quand un étudiant soumet une réponse
# ================================================================

@app.post("/evaluate", response_model=EvaluationResponse)
def evaluate_student_answer(request: EvaluationRequest):
    """
    Pipeline complet :
      1. Sympy vérifie la réponse (déterministe)
      2. LLM diagnostique la lacune (seulement si incorrect)

    Spring Boot envoie :
      - equation       : la question posée
      - student_answer : réponse de l'étudiant
      - exercise_type  : "equation" | "derivative" | "integral" | "limit"
      - expected_sympy : bonne réponse format Sympy (depuis la DB)
      - sympy_context  : infos optionnelles pour Sympy

    Spring Boot reçoit :
      - correct              : true / false
      - weakness_node_code   : ex "DERIVATIVE_PRODUCT" (si incorrect)
      - explanation          : explication pour l'étudiant (si incorrect)
      - confidence           : niveau de confiance du LLM (si incorrect)
    """
    # TODO : récupérer les knowledge_nodes depuis la DB PostgreSQL
    # Pour l'instant on utilise une liste statique pour tester
    # Ce sera remplacé par un appel à la DB dans la prochaine itération
    knowledge_nodes = [
        {"node_code": "DERIVATIVE_PRODUCT", "title": "Dérivée d'un produit"},
        {"node_code": "DERIVATIVE_CHAIN",   "title": "Dérivée par composition"},
        {"node_code": "SOLVE_LINEAR_EQ",    "title": "Équation du 1er degré"},
        {"node_code": "SOLVE_QUADRATIC_EQ", "title": "Équation du 2nd degré"},
        {"node_code": "INTEGRAL_BASIC",     "title": "Intégrale de base"},
        {"node_code": "LIMIT_BASIC",        "title": "Limite de base"},
    ]

    try:
        return evaluate(request, knowledge_nodes)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))