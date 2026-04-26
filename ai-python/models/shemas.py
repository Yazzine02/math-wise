from pydantic import BaseModel
from typing import Optional


# ================================================================
# REQUÊTE ENTRANTE
# Envoyée par Spring Boot vers FastAPI
# ================================================================

class EvaluationRequest(BaseModel):
    """
    Ce que Spring Boot envoie à FastAPI quand un étudiant soumet une réponse.

    Champs hérités du code existant :
      - equation      : la question posée (texte lisible)
      - student_answer: ce que l'étudiant a tapé

    Champs ajoutés pour le pipeline Sympy :
      - exercise_type : "equation" | "derivative" | "integral" | "limit"
      - expected_sympy: la bonne réponse en format Sympy (depuis la DB)
      - sympy_context : infos optionnelles pour Sympy (variable, bornes...)
    """
    # Existant
    equation: str
    student_answer: str

    # Nouveau — vient de la table exercises
    exercise_type: str
    expected_sympy: str
    sympy_context: str = ""


# ================================================================
# RÉSULTAT SYMPY
# Interne — circule entre verifier.py et diagnostician.py
# Jamais retourné directement au client
# ================================================================

class VerificationResult(BaseModel):
    """
    Résultat de la vérification déterministe par Sympy.
    Si correct = False, error_detail est transmis au LLM
    pour qu'il puisse diagnostiquer précisément la lacune.
    """
    correct: bool
    expected_str: str       # bonne réponse, forme simplifiée par Sympy
    student_str: str        # réponse étudiant, forme simplifiée par Sympy
    error_detail: str = ""  # description de l'erreur → input du LLM


# ================================================================
# RÉSULTAT LLM
# Interne — circule entre diagnostician.py et pipeline.py
# ================================================================

class DiagnosisResult(BaseModel):
    """
    Ce que le LLM retourne après analyse de l'erreur.
    weakness_node_code doit correspondre à un node_code
    existant dans la table knowledge_nodes.
    """
    weakness_node_code: str     # ex: "DERIVATIVE_PRODUCT"
    confidence: float           # entre 0 et 1
    explanation: str            # explication pédagogique pour l'étudiant


# ================================================================
# RÉPONSE FINALE
# Retournée par FastAPI à Spring Boot, puis à Flutter
# ================================================================

class EvaluationResponse(BaseModel):
    """
    Réponse complète retournée au client.

    Si correct = True  : seul 'correct' est rempli, le reste est None.
    Si correct = False : weakness_node_code et explanation sont remplis.
    """
    correct: bool
    weakness_node_code: Optional[str] = None
    explanation: Optional[str] = None
    confidence: Optional[float] = None