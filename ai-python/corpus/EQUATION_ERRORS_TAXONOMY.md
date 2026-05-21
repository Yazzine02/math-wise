# Taxonomie des erreurs-types en équations — Niveau Terminale
# Document RAG + Few-shot — MathsWise

Ce document catalogue les erreurs les plus fréquentes des élèves de Terminale
sur les équations, avec pour chaque erreur : la description, un exemple concret,
l'explication pédagogique, et le code de lacune associé.

---

## CATÉGORIE 1 — Équations du 1er degré

### Erreur 1.1 — Mauvais passage de terme (signe oublié ou inversé)
**Code lacune :** `EQ1_SIGN_ERROR`
**Fréquence :** Très fréquente

**Description :** L'élève passe un terme d'un membre à l'autre sans changer son signe.

**Exemple d'erreur :**
```
Résoudre : 3x + 5 = 11
Erreur élève : 3x = 11 + 5 = 16  →  x = 16/3
Correct      : 3x = 11 - 5 = 6   →  x = 2
```

**Explication pédagogique :**
Quand on passe un terme d'un membre à l'autre d'une égalité, on change son signe.
Soustraire 5 des deux membres donne : 3x + 5 - 5 = 11 - 5, soit 3x = 6.
La règle "je change de côté, je change de signe" s'applique à TOUS les termes.

**Erreur liée :** Confusion entre "changer de côté" et "changer de signe" uniquement pour la multiplication.

---

### Erreur 1.2 — Division par le coefficient sans traiter tous les membres
**Code lacune :** `EQ1_DIVISION_ERROR`
**Fréquence :** Fréquente

**Description :** L'élève divise un seul terme par le coefficient au lieu de diviser toute l'égalité.

**Exemple d'erreur :**
```
Résoudre : 4x + 8 = 20
Erreur élève : 4x/4 + 8 = 20  →  x + 8 = 20  →  x = 12
Correct      : (4x + 8)/4 = 20/4  →  x + 2 = 5  →  x = 3
Ou bien      : 4x = 20 - 8 = 12  →  x = 3
```

**Explication pédagogique :**
Une égalité reste vraie si on effectue la MÊME opération sur les DEUX membres en entier.
Diviser par 4 signifie diviser TOUTE l'expression de chaque membre, pas seulement le terme en x.

---

### Erreur 1.3 — Équation avec fractions : oubli du dénominateur commun
**Code lacune :** `EQ1_FRACTION_ERROR`
**Fréquence :** Très fréquente

**Description :** L'élève simplifie les fractions séparément sans mettre au même dénominateur.

**Exemple d'erreur :**
```
Résoudre : x/2 + x/3 = 5
Erreur élève : x + x = 5  →  2x = 5  →  x = 5/2
Correct      : 3x/6 + 2x/6 = 5  →  5x/6 = 5  →  x = 6
```

**Explication pédagogique :**
Pour additionner des fractions, il faut les mettre au même dénominateur.
Le PPCM de 2 et 3 est 6 : x/2 = 3x/6 et x/3 = 2x/6.
Multiplier les deux membres par 6 est une méthode efficace pour "effacer" les fractions.

---

### Erreur 1.4 — Valeur absolue : oubli du cas négatif
**Code lacune :** `EQ1_ABSOLUTE_VALUE_ERROR`
**Fréquence :** Fréquente

**Description :** L'élève résout |ax + b| = c en ne traitant qu'un seul cas.

**Exemple d'erreur :**
```
Résoudre : |2x - 3| = 5
Erreur élève : 2x - 3 = 5  →  x = 4  (un seul cas traité)
Correct      : Cas 1 : 2x - 3 = 5  →  x = 4
               Cas 2 : 2x - 3 = -5 →  x = -1
               S = {-1 ; 4}
```

**Explication pédagogique :**
|A| = c (avec c > 0) équivaut à A = c OU A = -c.
La valeur absolue représente une distance : la distance de 2x-3 à 0 vaut 5,
donc 2x-3 se trouve à 5 unités de 0, soit en +5 ou en -5.
Si c < 0, l'équation n'a pas de solution (une distance est toujours positive).

---

## CATÉGORIE 2 — Équations du 2ème degré

### Erreur 2.1 — Calcul du discriminant : erreur de signe dans b² - 4ac
**Code lacune :** `EQ2_DISCRIMINANT_SIGN`
**Fréquence :** Très fréquente

**Description :** L'élève fait une erreur de signe sur le terme -4ac, notamment quand a ou c est négatif.

**Exemple d'erreur :**
```
Résoudre : 2x² - 3x - 2 = 0  (a=2, b=-3, c=-2)
Erreur élève : Δ = (-3)² - 4×2×(-2) = 9 - 16 = -7  (signe de 4ac inversé)
Correct      : Δ = (-3)² - 4×2×(-2) = 9 + 16 = 25
               √Δ = 5
               x₁ = (3+5)/4 = 2  ;  x₂ = (3-5)/4 = -1/2
```

**Explication pédagogique :**
Δ = b² - 4ac. Attention : -4ac avec c négatif donne -4×(positif)×(négatif) = positif.
Méthode conseillée : identifier d'abord a, b, c avec leurs signes, puis calculer 4ac, puis soustraire.
Ici : 4ac = 4×2×(-2) = -16, donc Δ = 9 - (-16) = 9 + 16 = 25.

---

### Erreur 2.2 — Formule des racines : oubli du 2a au dénominateur
**Code lacune :** `EQ2_ROOT_FORMULA`
**Fréquence :** Fréquente

**Description :** L'élève écrit x = (-b ± √Δ) / a au lieu de (-b ± √Δ) / 2a.

**Exemple d'erreur :**
```
Résoudre : 3x² + 6x + 3 = 0  (a=3, b=6, c=3, Δ=0)
Erreur élève : x = -6/3 = -2
Correct      : x = -6/(2×3) = -6/6 = -1
```

**Explication pédagogique :**
La formule complète est x = (-b ± √Δ) / (2a). Le dénominateur est 2a, pas a.
Moyen mnémotechnique : la formule vient de la complétion du carré, où le coefficient 2
apparaît naturellement. Toujours écrire 2a au dénominateur, même si on risque de simplifier après.

---

### Erreur 2.3 — Équation non ramenée à la forme ax² + bx + c = 0
**Code lacune :** `EQ2_STANDARD_FORM`
**Fréquence :** Très fréquente

**Description :** L'élève applique le discriminant sans avoir mis l'équation sous forme standard.

**Exemple d'erreur :**
```
Résoudre : x² + 3x = 4
Erreur élève : a=1, b=3, c=4  →  Δ = 9 - 16 = -7  (c mal identifié)
Correct      : x² + 3x - 4 = 0  →  a=1, b=3, c=-4
               Δ = 9 + 16 = 25  →  x₁=1, x₂=-4
```

**Explication pédagogique :**
TOUJOURS ramener l'équation à ax² + bx + c = 0 (tout à gauche, 0 à droite) AVANT
d'identifier a, b, c. Ici, passer 4 à gauche donne c = -4, pas c = +4.

---

### Erreur 2.4 — Factorisation : erreur dans la forme (x - x₁)(x - x₂)
**Code lacune :** `EQ2_FACTORIZATION`
**Fréquence :** Fréquente

**Description :** L'élève oublie le coefficient a dans la forme factorisée ou se trompe de signe.

**Exemple d'erreur :**
```
Factoriser : 2x² - 6x + 4  (racines x₁=1, x₂=2)
Erreur élève : (x - 1)(x - 2)  (oubli du coefficient a=2)
Correct      : 2(x - 1)(x - 2)
Vérification : 2(x²-3x+2) = 2x²-6x+4 ✓
```

**Explication pédagogique :**
La forme factorisée est a(x - x₁)(x - x₂) avec a = coefficient de x².
Sans le a, le polynôme obtenu n'est pas équivalent à l'original.
Toujours vérifier en développant que les deux formes sont bien égales.

---

### Erreur 2.5 — Discriminant nul : annoncer deux racines au lieu d'une
**Code lacune :** `EQ2_DOUBLE_ROOT`
**Fréquence :** Modérée

**Description :** Quand Δ = 0, l'élève annonce x₁ et x₂ distincts ou oublie le cas.

**Exemple d'erreur :**
```
Résoudre : x² - 4x + 4 = 0  (Δ = 16 - 16 = 0)
Erreur élève : x₁ = (4+0)/2 = 2  et  x₂ = (4-0)/2 = 2  →  "deux solutions : 2 et 2"
Correct      : Δ = 0 donc racine double : x₀ = -b/2a = 4/2 = 2
               Une seule solution : x = 2  (avec multiplicité 2)
               Forme factorisée : (x-2)²
```

**Explication pédagogique :**
Δ = 0 signifie une racine double, c'est-à-dire une unique valeur de x solution.
La forme factorisée est a(x - x₀)². Dire "x₁ = 2 et x₂ = 2" est incorrect formellement :
l'ensemble solution est {2}, pas {2 ; 2}.

---

## CATÉGORIE 3 — Équations avec puissances

### Erreur 3.1 — xⁿ = k avec n pair : oubli de la racine négative
**Code lacune :** `EQ_POWER_EVEN_ROOT`
**Fréquence :** Très fréquente

**Description :** Pour x² = k ou x⁴ = k, l'élève ne donne que la racine positive.

**Exemple d'erreur :**
```
Résoudre : x² = 9
Erreur élève : x = 3
Correct      : x = 3 ou x = -3  (noté x = ±3)

Résoudre : x⁴ = 16
Erreur élève : x = 2
Correct      : x² = 4 (en posant X = x²)  →  x = ±2
```

**Explication pédagogique :**
Si n est PAIR, xⁿ = k (k > 0) a DEUX solutions : x = ⁿ√k et x = -ⁿ√k.
La fonction x ↦ xⁿ est paire (symétrique par rapport à 0), donc si x est solution, -x l'est aussi.
Si k < 0, pas de solution réelle (puissance paire toujours positive).
Si k = 0, unique solution x = 0.

---

### Erreur 3.2 — xⁿ = k avec n impair : chercher plusieurs racines
**Code lacune :** `EQ_POWER_ODD_ROOT`
**Fréquence :** Modérée

**Description :** L'élève cherche deux racines pour une puissance impaire.

**Exemple d'erreur :**
```
Résoudre : x³ = -8
Erreur élève : "pas de solution car racine d'un nombre négatif"
              ou "x = ±2"
Correct      : x = ∛(-8) = -2  (unique solution réelle)
```

**Explication pédagogique :**
Si n est IMPAIR, xⁿ = k a exactement UNE solution réelle : x = ⁿ√k, même si k < 0.
La fonction x ↦ xⁿ est impaire et strictement croissante sur ℝ, donc bijective.
∛(-8) = -2 car (-2)³ = -8. Pas de confusion avec les racines carrées.

---

### Erreur 3.3 — Substitution non effectuée pour les équations bicarrées
**Code lacune :** `EQ_POWER_SUBSTITUTION`
**Fréquence :** Fréquente

**Description :** L'élève ne pense pas à poser X = x² pour résoudre ax⁴ + bx² + c = 0.

**Exemple d'erreur :**
```
Résoudre : x⁴ - 5x² + 4 = 0
Erreur élève : Tente d'appliquer directement le discriminant avec x⁴ (erreur de méthode)
Correct      : Poser X = x²  →  X² - 5X + 4 = 0
               Δ = 25 - 16 = 9  →  X₁ = 4, X₂ = 1
               x² = 4  →  x = ±2
               x² = 1  →  x = ±1
               S = {-2 ; -1 ; 1 ; 2}
```

**Explication pédagogique :**
Une équation bicarrée (puissances 4 et 2 uniquement) se résout par substitution X = x².
On obtient une équation du 2nd degré en X, qu'on sait résoudre.
Attention : chaque solution X > 0 donne DEUX valeurs de x (±√X).
Si X = 0 : une solution x = 0. Si X < 0 : pas de solution réelle.

---

### Erreur 3.4 — Puissances négatives : confusion avec l'opposé
**Code lacune :** `EQ_NEGATIVE_EXPONENT`
**Fréquence :** Fréquente

**Description :** L'élève confond x⁻ⁿ = -xⁿ au lieu de 1/xⁿ.

**Exemple d'erreur :**
```
Résoudre : x⁻² = 4
Erreur élève : -x² = 4  →  x² = -4  →  "pas de solution"
Correct      : 1/x² = 4  →  x² = 1/4  →  x = ±1/2
```

**Explication pédagogique :**
Par définition, x⁻ⁿ = 1/xⁿ (pour x ≠ 0). Ce n'est PAS l'opposé de xⁿ.
x⁻² = 4 signifie 1/x² = 4, donc x² = 1/4, donc x = ±1/2.
Vérification : (1/2)⁻² = 1/(1/4) = 4 ✓ et (-1/2)⁻² = 4 ✓

---

### Erreur 3.5 — Développement de (a+b)² confondu avec a² + b²
**Code lacune :** `EQ_POWER_EXPANSION`
**Fréquence :** Très fréquente

**Description :** L'élève écrit (x+3)² = x² + 9 au lieu de x² + 6x + 9.

**Exemple d'erreur :**
```
Développer et résoudre : (x+3)² = 25
Erreur élève : x² + 9 = 25  →  x² = 16  →  x = ±4
Correct      : x² + 6x + 9 = 25  →  x² + 6x - 16 = 0
               Δ = 36 + 64 = 100  →  x = 2 ou x = -8

Ou méthode directe : x+3 = ±5  →  x = 2 ou x = -8
```

**Explication pédagogique :**
(a+b)² = a² + 2ab + b². Le terme croisé 2ab est TOUJOURS présent.
(x+3)² = x² + 2×x×3 + 3² = x² + 6x + 9.
Méthode directe recommandée ici : (x+3)² = 25 → x+3 = ±5 → résoudre deux équations du 1er degré.

---

## RÉCAPITULATIF — Table des codes de lacunes

| Code | Description courte | Catégorie |
|---|---|---|
| `EQ1_SIGN_ERROR` | Mauvais signe au passage de terme | 1er degré |
| `EQ1_DIVISION_ERROR` | Division partielle de l'égalité | 1er degré |
| `EQ1_FRACTION_ERROR` | Oubli du dénominateur commun | 1er degré |
| `EQ1_ABSOLUTE_VALUE_ERROR` | Oubli du cas négatif en valeur absolue | 1er degré |
| `EQ2_DISCRIMINANT_SIGN` | Erreur de signe dans b²-4ac | 2ème degré |
| `EQ2_ROOT_FORMULA` | Oubli du 2a au dénominateur | 2ème degré |
| `EQ2_STANDARD_FORM` | Équation non mise sous forme standard | 2ème degré |
| `EQ2_FACTORIZATION` | Oubli du coefficient a dans la factorisation | 2ème degré |
| `EQ2_DOUBLE_ROOT` | Racine double mal interprétée | 2ème degré |
| `EQ_POWER_EVEN_ROOT` | Oubli de ±√k pour puissance paire | Puissances |
| `EQ_POWER_ODD_ROOT` | Confusion sur racine de puissance impaire | Puissances |
| `EQ_POWER_SUBSTITUTION` | Substitution bicarrée non effectuée | Puissances |
| `EQ_NEGATIVE_EXPONENT` | Confusion x⁻ⁿ et -xⁿ | Puissances |
| `EQ_POWER_EXPANSION` | (a+b)² développé sans terme croisé | Puissances |