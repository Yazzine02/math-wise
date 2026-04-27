# math-wise

 
## Vision du projet
 
Math-Wise est une application mobile destinée aux élèves de Terminale qui veulent progresser en mathématiques sans payer un tuteur privé.
 
Le système fonctionne en trois temps :
 
1. **Diagnostic initial** — un quiz de 10 questions identifie les lacunes de l'étudiant dès la première session
2. **Remédiation ciblée** — des exercices personnalisés sur les concepts faibles, avec des explications pédagogiques quand l'étudiant se trompe
3. **Validation de la progression** — un quiz de validation confirme la maîtrise avant de passer au concept suivant
---

## Architecture
 
```
frontend-flutter/          ← Application mobile (iOS / Android)
        │
        ▼
backend-springboot/        ← API principale
  • Authentification JWT
  • Gestion des exercices
  • Orchestration vers le moteur IA
        │
        ├──────────────────────────┐
        ▼                          ▼
  PostgreSQL                   ai-python/
  • students                   • Moteur IA (FastAPI)
  • knowledge_nodes            • Sympy — vérification déterministe
  • interaction_logs           • LLM — diagnostic de lacune
  • exercises                  • RAG — explications pédagogiques 