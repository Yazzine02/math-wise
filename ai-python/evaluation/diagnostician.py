import json
from fastapi import HTTPException

from models.schemas import VerificationResult, DiagnosisResult
from adapters.ai_adapters import get_ai_adapter

# ================================================================
# PROMPT
# ================================================================

def _build_prompt(
    exercise_type: str,
    equation: str,
    verification: VerificationResult,
    knowledge_nodes: list[dict]
) -> str:
    """
    Construit un prompt contraint pour le LLM.

    Deux principes clés :
    1. Le LLM reçoit le détail de l'erreur calculé par Sympy
       → il diagnostique avec précision, pas en devinant
    2. Le LLM DOIT choisir parmi les knowledge_nodes existants
       → il ne peut pas inventer une lacune qui n'existe pas en DB
    """
    nodes_formatted = json.dumps(knowledge_nodes, ensure_ascii=False, indent=2)

    return f"""Tu es un expert en didactique des mathématiques pour le niveau Terminale.

Un étudiant a commis une erreur sur un exercice de type : {exercise_type.upper()}

--- DÉTAIL DE L'ERREUR ---
Question posée       : {equation}
Bonne réponse        : {verification.expected_str}
Réponse de l'étudiant: {verification.student_str}
Analyse mathématique : {verification.error_detail}

--- NŒUDS DE LACUNES DISPONIBLES ---
Tu dois obligatoirement choisir UN nœud parmi cette liste.
Ne pas inventer un nœud qui n'existe pas dans la liste.

{nodes_formatted}

--- INSTRUCTIONS ---
Analyse l'erreur et identifie la lacune la plus probable.
Réponds UNIQUEMENT avec un objet JSON valide, rien d'autre.
Pas de texte avant, pas de texte après, pas de balises markdown.

Format attendu :
{{
  "weakness_node_code": "<node_code parmi la liste ci-dessus>",
  "confidence": <nombre entre 0.0 et 1.0>,
  "explanation": "<explication claire, encourageante, max 2 phrases, pour l'étudiant>"
}}"""


# ================================================================
# PARSING DE LA RÉPONSE LLM
# ================================================================

def _parse_llm_response(raw: dict, valid_codes: list[str]) -> DiagnosisResult:
    """
    Valide et parse la réponse JSON du LLM.
    Vérifie que weakness_node_code est bien dans nos knowledge_nodes.
    """
    try:
        code        = raw.get("weakness_node_code", "").strip()
        confidence  = float(raw.get("confidence", 0.5))
        explanation = raw.get("explanation", "").strip()

        if not code:
            raise ValueError("weakness_node_code manquant dans la réponse LLM")

        # Sécurité : le LLM a peut-être inventé un code qui n'existe pas
        if code not in valid_codes:
            # On prend le premier nœud valide plutôt que de planter
            code = valid_codes[0] if valid_codes else "UNKNOWN"

        if not explanation:
            explanation = "Revois ce concept et réessaie — tu vas y arriver !"

        return DiagnosisResult(
            weakness_node_code=code,
            confidence=max(0.0, min(1.0, confidence)),
            explanation=explanation
        )

    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Réponse LLM invalide : {str(e)}. Réponse brute : {raw}"
        )


# ================================================================
# POINT D'ENTRÉE UNIQUE
# Appelé par pipeline.py uniquement si Sympy a dit incorrect
# ================================================================

def diagnose(
    exercise_type: str,
    equation: str,
    verification: VerificationResult,
    knowledge_nodes: list[dict]
) -> DiagnosisResult:
    """
    Appelle get_ai_adapter() pour obtenir l'adapter actif (local ou cloud)
    puis envoie le prompt au LLM et valide la réponse.

    Remarque : get_ai_adapter() lit AI_MODE depuis .env
    et retourne LocalModelAdapter ou CloudAPIAdapter automatiquement.
    Ici on ne sait pas lequel — et on s'en fiche.
    """
    # 1. Récupère l'adapter actif depuis .env — local ou cloud
    ai_adapter = get_ai_adapter()

    # 2. Construit le prompt avec le détail de l'erreur Sympy
    prompt = _build_prompt(exercise_type, equation, verification, knowledge_nodes)

    # 3. Appelle .evaluate() — même interface peu importe l'adapter
    try:
        raw_response = ai_adapter.evaluate(prompt)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur LLM : {str(e)}")

    # 4. Valide que le node_code retourné existe bien en DB
    valid_codes = [node["node_code"] for node in knowledge_nodes]
    return _parse_llm_response(raw_response, valid_codes)