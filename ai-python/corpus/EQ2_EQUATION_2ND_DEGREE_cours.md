# Équations du 2ème degré — Cours complet
# Niveau : Terminale (Bac général spécialité maths)
# knowledge_node_code : EQ2

---

## 1. Définition et forme générale

Une équation du 2ème degré est une équation de la forme :

**ax² + bx + c = 0**  avec a ≠ 0, a, b, c ∈ ℝ

**Étape préalable OBLIGATOIRE :** Toujours ramener l'équation à cette forme standard
(tout à gauche, 0 à droite) avant d'identifier a, b, c.

**Exemples :**
- x² - 5x + 6 = 0 : a=1, b=-5, c=6
- 2x² + 3x = 0 : a=2, b=3, c=0
- x² = 9 : a=1, b=0, c=-9 (après x² - 9 = 0)
- 3x² - x = 2x² + 4 : a=1, b=-1, c=-4 (après simplification)

---

## 2. Le discriminant Δ (Delta)

### Définition
**Δ = b² - 4ac**

Le discriminant détermine le nombre et la nature des solutions.

### Calcul pas à pas
1. Identifier a, b, c avec leurs signes
2. Calculer b² (toujours positif)
3. Calculer 4ac (attention aux signes !)
4. Soustraire : Δ = b² - 4ac

**Exemple détaillé :**
```
2x² - 3x - 2 = 0  →  a=2, b=-3, c=-2

b² = (-3)² = 9
4ac = 4 × 2 × (-2) = -16
Δ = b² - 4ac = 9 - (-16) = 9 + 16 = 25
```

**Erreur classique :** Quand c < 0, le terme -4ac est positif (moins × moins = plus).
Ne pas confondre le signe de c et le signe de -4ac.

---

## 3. Les trois cas selon le signe de Δ

### Cas 1 : Δ > 0 — Deux solutions réelles distinctes

$$x_1 = \frac{-b + \sqrt{\Delta}}{2a} \quad \text{et} \quad x_2 = \frac{-b - \sqrt{\Delta}}{2a}$$

**Le dénominateur est 2a, pas a.**

```
Résoudre : 2x² - 3x - 2 = 0  (Δ = 25, √Δ = 5)

x₁ = (-(-3) + 5) / (2×2) = (3+5)/4 = 8/4 = 2
x₂ = (-(-3) - 5) / (2×2) = (3-5)/4 = -2/4 = -1/2

S = {-1/2 ; 2}

Vérification :
2×(2)² - 3×2 - 2 = 8 - 6 - 2 = 0 ✓
2×(1/4) - 3×(-1/2) - 2 = 1/2 + 3/2 - 2 = 0 ✓
```

### Cas 2 : Δ = 0 — Une racine double (unique solution)

$$x_0 = \frac{-b}{2a}$$

**Il y a UNE seule solution** (pas "deux solutions égales").

```
Résoudre : x² - 4x + 4 = 0  →  a=1, b=-4, c=4
Δ = 16 - 16 = 0
x₀ = -(-4)/(2×1) = 4/2 = 2

S = {2}
Forme factorisée : (x-2)²
```

### Cas 3 : Δ < 0 — Aucune solution réelle

L'équation n'admet aucune solution dans ℝ (elle a deux solutions complexes, hors programme Terminale).

```
Résoudre : x² + x + 1 = 0  →  a=1, b=1, c=1
Δ = 1 - 4 = -3 < 0

S = ∅  (pas de solution réelle)
```

---

## 4. Forme factorisée

Quand Δ ≥ 0, le trinôme se factorise :

**ax² + bx + c = a(x - x₁)(x - x₂)**

Et si Δ = 0 : **ax² + bx + c = a(x - x₀)²**

**Attention :** Le coefficient **a** est OBLIGATOIRE dans la forme factorisée.

```
Factoriser : 3x² - 7x + 2 = 0

Δ = 49 - 24 = 25, √Δ = 5
x₁ = (7+5)/6 = 2  ;  x₂ = (7-5)/6 = 1/3

Forme factorisée : 3(x-2)(x-1/3) = (x-2)(3x-1)

Vérification : (x-2)(3x-1) = 3x²-x-6x+2 = 3x²-7x+2 ✓
```

**Propriété clé :** ax² + bx + c = 0 ⟺ (x - x₁)(x - x₂) = 0 ⟺ x = x₁ ou x = x₂

---

## 5. Relations coefficients-racines (formules de Viète)

Si x₁ et x₂ sont les racines de ax² + bx + c = 0 :

**Somme des racines :** x₁ + x₂ = -b/a

**Produit des racines :** x₁ × x₂ = c/a

```
Pour x² - 5x + 6 = 0 (a=1, b=-5, c=6) :
Somme : x₁ + x₂ = 5/1 = 5
Produit : x₁ × x₂ = 6/1 = 6
→ Racines : 2 et 3  (2+3=5 ✓  2×3=6 ✓)
```

**Usage :** Permet de trouver les racines rapidement quand elles sont entières,
ou de vérifier un résultat sans calculer le discriminant.

---

## 6. Signe du trinôme

Le signe de ax² + bx + c dépend de a et de Δ.

### Δ > 0 (deux racines x₁ < x₂)

| Intervalle | Signe de ax² + bx + c |
|---|---|
| x < x₁ | Même signe que a |
| x₁ < x < x₂ | Signe opposé à a |
| x > x₂ | Même signe que a |

Règle mnémotechnique : **"le trinôme est du signe de a à l'extérieur des racines"**

### Δ = 0 (racine double x₀)

Le trinôme est du signe de a partout (sauf nul en x₀).

### Δ < 0

Le trinôme est du signe de a pour tout x (toujours du même signe).

```
Étudier le signe de -2x² + 5x + 3

a = -2, b = 5, c = 3
Δ = 25 + 24 = 49  →  x₁ = (−5−7)/(−4) = 3  ;  x₂ = (−5+7)/(−4) = −1/2

Comme a < 0 :
- Positif pour -1/2 < x < 3
- Négatif pour x < -1/2 ou x > 3
```

---

## 7. Méthodes alternatives de résolution

### 7.1 — Factorisation directe (sans discriminant)

Parfois visible à l'inspection :

```
x² + 5x + 6 = 0  →  chercher deux nombres de somme 5 et produit 6
→  2 et 3  →  (x+2)(x+3) = 0  →  x = -2 ou x = -3
```

### 7.2 — Mise en facteur (terme constant nul)

Si c = 0 : ax² + bx = 0 → x(ax + b) = 0

```
3x² - 6x = 0  →  3x(x-2) = 0  →  x = 0 ou x = 2
Ne JAMAIS diviser par x (on perdrait la solution x=0)
```

### 7.3 — Identités remarquables

```
x² - 9 = 0  →  (x-3)(x+3) = 0  →  x = ±3
4x² - 4x + 1 = 0  →  (2x-1)² = 0  →  x = 1/2 (racine double)
```

### 7.4 — Complétion du carré

Utile pour comprendre la structure géométrique :

```
x² + 6x + 5 = 0
(x+3)² - 9 + 5 = 0
(x+3)² = 4
x+3 = ±2
x = -1 ou x = -5
```

---

## 8. Équations se ramenant au 2ème degré

### Substitution simple

```
Résoudre : (2x+1)² - 3(2x+1) + 2 = 0
Poser X = 2x+1 : X² - 3X + 2 = 0  →  (X-1)(X-2) = 0
X = 1 → 2x+1 = 1 → x = 0
X = 2 → 2x+1 = 2 → x = 1/2
```

---

## 9. Résolution graphique

ax² + bx + c = 0 : chercher les abscisses des points d'intersection
de la parabole y = ax²+bx+c avec l'axe des abscisses.

**Sommet de la parabole :** abscisse xs = -b/(2a), ordonnée ys = -Δ/(4a)

- Δ > 0 : la parabole coupe l'axe en deux points
- Δ = 0 : la parabole est tangente à l'axe
- Δ < 0 : la parabole ne coupe pas l'axe

---

## 10. Points de vigilance

| Erreur | Mauvais | Correct |
|---|---|---|
| Identification de c | x²+3x=4 → c=4 | Ramener à x²+3x-4=0 → c=-4 |
| Signe de b | b=-3 → b²=-9 | b²=(-3)²=9 (toujours ≥ 0) |
| Signe de -4ac | c=-2 → -4ac=-(-16)=-16 | -4×a×(-2)=+8a |
| Formule des racines | x = (-b±√Δ)/a | x = (-b±√Δ)/(2a) |
| Oubli du a dans factorisation | (x-x₁)(x-x₂) | a(x-x₁)(x-x₂) |
| Racine double = deux solutions | S={2;2} | S={2} (une racine double) |
| Division par x | 3x²=6x → 3x=6 | 3x²-6x=0 → 3x(x-2)=0 |