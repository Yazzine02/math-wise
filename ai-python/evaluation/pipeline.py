from models.shemas import EvaluationRequest, EvaluationResponse
from evaluation.verifier import verify
from evaluation.diagnostician import diagnose
from rag.retriever import retrieve_excerpts 


def evaluate(request: EvaluationRequest, knowledge_nodes: list[dict]) -> EvaluationResponse:
    """
    Point d'entrée principal appelé par main.py.

    Étape 1 — Sympy vérifie (toujours)
    Étape 2 — LLM diagnostique (seulement si incorrect)

    Args:
        request         : données envoyées par Spring Boot
        knowledge_nodes : liste des nœuds depuis la DB
                          [{"node_code": "...", "title": "..."}]

    Returns:
        EvaluationResponse retournée à Spring Boot puis Flutter
    """

    # ----------------------------------------------------------------
    # ÉTAPE 1 — SYMPY
    # Déterministe, infaillible, instantané
    # ----------------------------------------------------------------
    verification = verify(
        exercise_type=request.exercise_type,
        expected_sympy=request.expected_sympy,
        student_answer=request.student_answer,
        sympy_context=request.sympy_context
    )

    # ----------------------------------------------------------------
    # ÉTAPE 2 — RÉPONSE SI CORRECT
    # Le LLM n'est jamais appelé si l'étudiant a bon
    # ----------------------------------------------------------------
    if verification.correct:
        return EvaluationResponse(correct=True)
    
    # ----------------------------------------------------------------
    # ÉTAPE 3 — RAG : récupère les extraits de cours pertinents
    # Retourne [] si ChromaDB vide ou absent — pas de plantage
    # ----------------------------------------------------------------
    rag_excerpts = retrieve_excerpts(
        query=f"{request.exercise_type} {verification.error_detail}",
        k=3
    )

    # ----------------------------------------------------------------
    # ÉTAPE 4 — LLM DIAGNOSTIC SI INCORRECT
    # Le LLM reçoit le détail de l'erreur calculé par Sympy
    # Il choisit parmi les knowledge_nodes existants en DB
    # ----------------------------------------------------------------
    diagnosis = diagnose(
        exercise_type=request.exercise_type,
        equation=request.equation,
        verification=verification,
        knowledge_nodes=knowledge_nodes,
        rag_excerpts=rag_excerpts  
    )

    return EvaluationResponse(
        correct=False,
        weakness_node_code=diagnosis.weakness_node_code,
        explanation=diagnosis.explanation,
        confidence=diagnosis.confidence
    )