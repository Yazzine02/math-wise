# Math-Wise AI Service — Changelog
 
All notable changes to the AI service (`ai-python`) are documented here. 
---
 
## [Unreleased] — En cours
 
### Planned
- `RAG` — Retrieval Augmented Generation sur contenu 
- `interaction_logs` — sauvegarde du résultat après caque évaluation
- `knowledge_nodes` — remplacement de la liste statique par un vrai appel PostgreSQL
- `/diagnostic` — endpoint de diagnostic initial (quiz de 10 questions)
- `/profile` — endpoint de profil de progression par étudiant
---


---
## Current Configuration

## 2026-04-27 — Refactoring IA + Pipeline déterministe
 
> Auteur : Hafsa
> Branche : `feat/sympy-deterministic-evaluation`
 
### Contexte
La V1 confiait la vérification mathématique au LLM — un modèle de langage
peut halluciner sur du calcul pur. Cette version introduit une séparation stricte
des responsabilités : Sympy vérifie, le LLM diagnostique.
 
### Added
- `models/shemas.py` — modèles Pydantic centralisés pour tout le pipeline
  - `EvaluationRequest` : requête entrante depuis Spring Boot
  - `VerificationResult` : résultat interne Sympy (jamais exposé au client)
  - `DiagnosisResult` : résultat interne LLM
  - `EvaluationResponse` : réponse finale vers Flutter
- `evaluation/verifier.py` — vérification déterministe avec Sympy
  - Support des 4 types : `equation`, `derivative`, `integral`, `limit`
  - Équivalence via `simplify(expected - student) == 0`
  - Cas spécial intégrales indéfinies : comparaison des dérivées pour ignorer la constante C
  - Retourne `error_detail` précis transmis au LLM si incorrect
- `evaluation/diagnostician.py` — diagnostic LLM contraint
  - Appelé **uniquement** si Sympy retourne `correct=False`
  - Prompt construit avec le détail de l'erreur Sympy
  - LLM forcé à choisir parmi les `knowledge_nodes` existants en DB
  - Validation de la réponse LLM — fallback si `node_code` inconnu
- `evaluation/pipeline.py` — orchestrateur du pipeline complet
  - Étape 1 : Sympy (toujours)
  - Étape 2 : LLM (seulement si incorrect)
  - Interface unique appelée par `main.py`
- `adapters/ai_adapters.py` — adapters IA isolés dans leur module
  - `AIEngineAdapter` : interface abstraite
  - `LocalModelAdapter` : Ollama (llama3.1)
  - `CloudAPIAdapter` : API compatible OpenAI
  - `get_ai_adapter()` : factory qui lit `AI_MODE` depuis `.env`
- `entity/Exercise.java` — entité Hibernate côté Spring Boot
  - `ExerciseType` enum : `EQUATION`, `DERIVATIVE`, `INTEGRAL`, `LIMIT`
  - `questionDisplay` : ce que l'étudiant voit dans Flutter
  - `expectedSympy` : bonne réponse lisible par Sympy
  - `sympyContext` : infos optionnelles (variable, bornes, point)
  - `difficultyLevel` : 1 à 3 pour le moteur de recommandation
  - Lié à `KnowledgeNode` via `@ManyToOne LAZY`
### Changed
- `main.py` — allégé, contient uniquement les endpoints HTTP
  - Suppression des adapters et modèles inline
  - Endpoint `/evaluate-error` renommé `/evaluate`
  - Ajout endpoint `/health` pour Spring Boot
  - `knowledge_nodes` statique en attendant la connexion DB
### Validated
- `correct: true` — Sympy confirme, LLM non appelé ✓
- `correct: false` — Sympy détecte, LLM diagnostique ✓
- Testé via Swagger UI sur `http://127.0.0.1:8000/docs`
---

## Initial — Pipeline IA de base
 
> Auteur : Yassine 
> Branche : `chore/phase-0-hardening`
 
### Added
- `main.py` — FastAPI avec endpoint `/evaluate-error`
- `AIEngineAdapter` — interface abstraite ABC
- `LocalModelAdapter` — connexion Ollama local
- `CloudAPIAdapter` — connexion API cloud compatible OpenAI
- `get_ai_adapter()` — factory basée sur `AI_MODE` dans `.env`
- `.env.example` — template de configuration
### Known Issues
- LLM responsable de la vérification mathématique ET du diagnostic
  → risque d'hallucination sur le calcul
- Résultat jamais sauvegardé dans `interaction_logs`
- `knowledge_nodes` non utilisés dans le diagnostic
- Sécurité JWT désactivée (`/**` public)

