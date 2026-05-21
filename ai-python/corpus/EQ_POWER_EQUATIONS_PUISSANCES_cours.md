# Équations avec puissances — Cours complet
# Niveau : Terminale (Bac général spécialité maths)
# knowledge_node_code : EQ_POWER

---

## 1. Rappels — Définitions et propriétés des puissances

### Puissances entières positives
xⁿ = x × x × ... × x  (n facteurs)

### Puissances entières nulles et négatives
x⁰ = 1  (pour x ≠ 0)
x⁻ⁿ = 1/xⁿ  (pour x ≠ 0)

**Attention :** x⁻ⁿ ≠ -xⁿ  (erreur très fréquente)
- 2⁻³ = 1/8  ≠  -8
- x⁻¹ = 1/x  ≠  -x

### Puissances fractionnaires
x^(1/n) = ⁿ√x  (racine n-ième)
x^(p/q) = (ⁿ√x)^p = ⁿ√(xᵖ)

### Propriétés opératoires (pour a, b > 0)
- aⁿ × aᵐ = aⁿ⁺ᵐ
- aⁿ / aᵐ = aⁿ⁻ᵐ
- (aⁿ)ᵐ = aⁿˣᵐ
- (ab)ⁿ = aⁿ × bⁿ
- (a/b)ⁿ = aⁿ/bⁿ

---

## 2. Équations de la forme xⁿ = k

### Cas n pair (n = 2, 4, 6, ...)

La fonction x ↦ xⁿ est **paire** : (-x)ⁿ = xⁿ

**Si k > 0 :** Deux solutions : x = ⁿ√k et x = -ⁿ√k  (noté x = ±ⁿ√k)

**Si k = 0 :** Une solution : x = 0

**Si k < 0 :** Aucune solution réelle (xⁿ ≥ 0 pour n pair)

```
Exemples :

x² = 7       →  x = ±√7
x² = 0       →  x = 0  (unique solution)
x² = -4      →  S = ∅  (pas de solution réelle)
x⁴ = 16      →  x = ±√(√16) = ±2
x⁴ = 81      →  x = ±∜81 = ±3
x⁶ = 64      →  x = ±⁶√64 = ±2
```

**Méthode pour n pair ≥ 4 :**
xⁿ = k  avec n=2m  →  poser X = x²  →  Xᵐ = k  →  résoudre en X, puis en x

```
x⁴ = 81  →  poser X = x²  →  X² = 81  →  X = ±9
X = 9  →  x² = 9  →  x = ±3
X = -9 →  x² = -9  →  pas de solution
S = {-3 ; 3}
```

### Cas n impair (n = 1, 3, 5, ...)

La fonction x ↦ xⁿ est **impaire** et **strictement croissante** sur ℝ : elle est bijective.

**Pour tout k réel :** Exactement une solution réelle : x = ⁿ√k

**Si k < 0 :** La racine n-ième d'un nombre négatif EXISTE et est négative.

```
Exemples :

x³ = 8       →  x = ∛8 = 2
x³ = -27     →  x = ∛(-27) = -3  (car (-3)³ = -27)
x³ = 0       →  x = 0
x⁵ = -32     →  x = ⁵√(-32) = -2  (car (-2)⁵ = -32)
x³ = 7       →  x = ∛7  ≈ 1,913
```

**Rappel :** ∛(-8) = -2 est un réel parfaitement défini. Ne pas confondre avec √(-8) qui n'existe pas dans ℝ.

---

## 3. Équations bicarrées — Substitution X = x²

### Forme : ax⁴ + bx² + c = 0

**Méthode :** Poser X = x² (avec X ≥ 0), résoudre en X, puis revenir en x.

```
Résoudre : x⁴ - 5x² + 4 = 0

Poser X = x²  (X ≥ 0) :
X² - 5X + 4 = 0
Δ = 25 - 16 = 9  →  X₁ = 4, X₂ = 1

Revenir en x :
X₁ = 4  →  x² = 4  →  x = ±2  ✓ (car X₁ ≥ 0)
X₂ = 1  →  x² = 1  →  x = ±1  ✓ (car X₂ ≥ 0)

S = {-2 ; -1 ; 1 ; 2}
```

**Cas où une racine en X est négative :**

```
Résoudre : x⁴ - 3x² - 4 = 0

X² - 3X - 4 = 0  →  (X-4)(X+1) = 0  →  X₁ = 4, X₂ = -1

X₁ = 4  →  x = ±2  ✓
X₂ = -1 →  x² = -1  →  IMPOSSIBLE (X₂ < 0, pas de solution réelle)

S = {-2 ; 2}
```

**Règle :** Rejeter toute valeur X < 0. Seules les valeurs X ≥ 0 donnent des solutions en x.

### Généralisations de la substitution

**Forme (f(x))² + b×f(x) + c = 0 :**

```
Résoudre : (x² + x)² - 2(x² + x) - 3 = 0

Poser X = x² + x :
X² - 2X - 3 = 0  →  (X-3)(X+1) = 0  →  X = 3 ou X = -1

X = 3  →  x² + x - 3 = 0  →  Δ = 1+12 = 13  →  x = (-1±√13)/2
X = -1 →  x² + x + 1 = 0  →  Δ = 1-4 = -3 < 0  →  pas de solution

S = {(-1-√13)/2 ; (-1+√13)/2}
```

---

## 4. Puissances négatives dans les équations

### Forme x⁻ⁿ = k  (équivalent à 1/xⁿ = k)

**Méthode :** Multiplier les deux membres par xⁿ, puis résoudre.

```
Résoudre : x⁻² = 4  (soit 1/x² = 4, x ≠ 0)

Multiplier par x² : 1 = 4x²
x² = 1/4
x = ±1/2

Vérification : (1/2)⁻² = 1/(1/4) = 4 ✓  et  (-1/2)⁻² = 4 ✓
```

```
Résoudre : x⁻³ = -8  (soit 1/x³ = -8, x ≠ 0)

x³ = -1/8
x = ∛(-1/8) = -1/2

Vérification : (-1/2)⁻³ = 1/(-1/8) = -8 ✓
```

### Forme x⁻¹ = k  (la plus simple)

```
1/x = 3  →  x = 1/3  (avec x ≠ 0)
```

---

## 5. Développements et identités remarquables avec puissances

### Carré d'une somme / différence

**(a+b)² = a² + 2ab + b²**
**(a-b)² = a² - 2ab + b²**

**Erreur classique :** (a+b)² ≠ a² + b²  (le terme croisé 2ab est OBLIGATOIRE)

```
(x+3)² = x² + 6x + 9  (pas x² + 9)
(2x-1)² = 4x² - 4x + 1

Résoudre : (x+3)² = 25

Méthode 1 (directe) : x+3 = ±5
    x+3 = 5  →  x = 2
    x+3 = -5 →  x = -8

Méthode 2 (développement) :
    x² + 6x + 9 = 25
    x² + 6x - 16 = 0
    Δ = 36 + 64 = 100  →  x = (-6±10)/2  →  x = 2 ou x = -8
```

### Différence de carrés

**(a-b)(a+b) = a² - b²**

```
Résoudre : x² - 7 = 0
→ (x-√7)(x+√7) = 0  →  x = ±√7
```

### Cube d'une somme

**(a+b)³ = a³ + 3a²b + 3ab² + b³**
**(a-b)³ = a³ - 3a²b + 3ab² - b³**

### Somme et différence de cubes

**a³ + b³ = (a+b)(a² - ab + b²)**
**a³ - b³ = (a-b)(a² + ab + b²)**

```
Factoriser : 8x³ - 1 = (2x)³ - 1³ = (2x-1)(4x²+2x+1)
Résoudre 8x³ = 1 : x = 1/2
```

---

## 6. Équations avec racines (puissances fractionnaires)

### Forme √(f(x)) = g(x)

**Condition d'existence :** f(x) ≥ 0 ET g(x) ≥ 0

**Méthode :** √(f(x)) = g(x) ⟺ f(x) = (g(x))² ET g(x) ≥ 0

```
Résoudre : √(x+2) = x  (condition : x ≥ 0 et x+2 ≥ 0, soit x ≥ 0)

Mise au carré : x+2 = x²
x² - x - 2 = 0
(x-2)(x+1) = 0  →  x = 2 ou x = -1

Vérification des conditions (x ≥ 0) :
x = 2  : √(2+2) = √4 = 2 ✓
x = -1 : ne vérifie pas x ≥ 0  →  à rejeter

S = {2}
```

**Attention :** TOUJOURS vérifier les solutions dans l'équation originale car la mise au carré peut introduire de fausses solutions.

---

## 7. Tableau récapitulatif — xⁿ = k

| n pair | k > 0 | k = 0 | k < 0 |
|---|---|---|---|
| **Solutions** | x = ±ⁿ√k | x = 0 | Aucune |
| **Nombre** | 2 | 1 | 0 |

| n impair | k > 0 | k = 0 | k < 0 |
|---|---|---|---|
| **Solutions** | x = ⁿ√k > 0 | x = 0 | x = ⁿ√k < 0 |
| **Nombre** | 1 | 1 | 1 |

---

## 8. Erreurs classiques à éviter

| Erreur | Exemple incorrect | Correction |
|---|---|---|
| Puissance négative = opposé | x⁻² = -x² | x⁻² = 1/x² |
| n pair → oubli de ± | x²=9 → x=3 | x = ±3 |
| n impair → deux racines | x³=-8 → x=±2 | x = -2 (unique) |
| ∛(négatif) impossible | ∛(-8) = impossible | ∛(-8) = -2 |
| Carré sans terme croisé | (x+3)²=x²+9 | (x+3)²=x²+6x+9 |
| Bicarrée : oublier de rejeter X<0 | x²=-1 → x=±i | Pas de solution réelle |
| Division par x | x³=x → x²=1 | x³-x=0 → x(x²-1)=0 |

---

## 9. Exercices types

**Exercice 1 :** Résoudre x⁴ - 13x² + 36 = 0
*Réponse :* X=x² → X²-13X+36=0 → X=4 ou X=9 → x=±2 ou x=±3. S={-3;-2;2;3}

**Exercice 2 :** Résoudre x⁻² - 3x⁻¹ + 2 = 0
*Réponse :* Poser X=x⁻¹=1/x : X²-3X+2=0 → X=1 ou X=2 → x=1 ou x=1/2

**Exercice 3 :** Résoudre (x²-2x)² = (x²-2x) + 6
*Réponse :* Poser X=x²-2x : X²-X-6=0 → X=3 ou X=-2
X=3 → x²-2x-3=0 → x=3 ou x=-1
X=-2 → x²-2x+2=0 → Δ=-4<0 → pas de solution
S={-1;3}