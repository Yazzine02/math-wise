# Équations du 1er degré — Cours complet
# Niveau : Terminale (Bac général spécialité maths)
# knowledge_node_code : EQ1

---

## 1. Définition et forme générale

Une équation du 1er degré à une inconnue x est une équation de la forme :

**ax + b = 0**  avec a ≠ 0, a et b des réels

Plus généralement, toute équation qui se ramène à cette forme après simplification.

**Exemples de formes rencontrées :**
- 3x + 7 = 0 (forme standard)
- 5x - 3 = 2x + 9 (termes des deux côtés)
- x/3 + 2 = x/2 - 1 (avec fractions)
- 2(x - 4) = 3(x + 1) (avec parenthèses)
- |2x - 1| = 5 (avec valeur absolue)

**Unique solution :** x = -b/a

---

## 2. Méthode générale de résolution

### Règles fondamentales d'équivalence

Une égalité reste vraie si l'on effectue la **même opération sur les deux membres**.

**Règle 1 — Addition/Soustraction :**
A = B  ⟺  A + k = B + k  (pour tout réel k)

**Règle 2 — Multiplication/Division :**
A = B  ⟺  A × k = B × k  (pour k ≠ 0)

### Principe "changer de côté = changer de signe"
Passer un terme de gauche à droite (ou inversement) revient à le soustraire des deux membres.

```
3x + 5 = 11
⟺  3x = 11 - 5    (on a "passé" +5 à droite, il devient -5)
⟺  3x = 6
⟺  x = 2
```

### Étapes recommandées
1. Développer les parenthèses
2. Rassembler les termes en x à gauche
3. Rassembler les constantes à droite
4. Diviser par le coefficient de x

**Exemple complet :**
```
Résoudre : 4(x - 2) + 3 = 2x + 7

Étape 1 — Développer :     4x - 8 + 3 = 2x + 7
Étape 2 — Simplifier :     4x - 5 = 2x + 7
Étape 3 — x à gauche :     4x - 2x = 7 + 5
Étape 4 — Simplifier :     2x = 12
Étape 5 — Diviser par 2 :  x = 6

Vérification : 4(6-2)+3 = 4×4+3 = 19  ;  2×6+7 = 19 ✓
```

---

## 3. Équations avec fractions

### Méthode — Mise au même dénominateur ou multiplication croisée

**Stratégie 1 : Multiplier les deux membres par le PPCM des dénominateurs**

```
Résoudre : x/2 + x/3 = 5

PPCM(2,3) = 6
Multiplier par 6 : 6×(x/2) + 6×(x/3) = 6×5
                    3x + 2x = 30
                    5x = 30
                    x = 6

Vérification : 6/2 + 6/3 = 3 + 2 = 5 ✓
```

**Stratégie 2 : Mise au même dénominateur terme par terme**

```
Résoudre : (2x+1)/3 = (x-2)/4

Mettre au même dénominateur (12) :
4(2x+1)/12 = 3(x-2)/12
Donc : 4(2x+1) = 3(x-2)
       8x + 4 = 3x - 6
       5x = -10
       x = -2
```

**Règle d'or :** Ne jamais simplifier une fraction contenant x sans avoir vérifié que le dénominateur ≠ 0 pour la valeur trouvée.

### Équations avec x au dénominateur

```
Résoudre : 1/x + 1/(x+1) = 1   (x ≠ 0 et x ≠ -1)

Multiplier par x(x+1) :
(x+1) + x = x(x+1)
2x + 1 = x² + x
0 = x² - x - 1

Discriminant : Δ = 1 + 4 = 5
x = (1 ± √5)/2

Vérifier que ces valeurs ≠ 0 et ≠ -1 : ✓ (les deux valeurs conviennent)
```

**Attention :** Quand x est au dénominateur, l'équation devient du 2ème degré après multiplication. Il faut traiter les deux racines et vérifier les contraintes.

---

## 4. Équations avec valeur absolue

### Définition rappel
|x| = x si x ≥ 0  ;  |x| = -x si x < 0
Géométriquement : |x| est la distance de x à 0 sur la droite réelle.

### Cas 1 — |ax + b| = c

**Si c < 0 :** Aucune solution (distance toujours positive)
**Si c = 0 :** Une solution : ax + b = 0 → x = -b/a
**Si c > 0 :** Deux cas à traiter :
- Cas 1 : ax + b = c
- Cas 2 : ax + b = -c

```
Résoudre : |3x - 6| = 9

Cas 1 : 3x - 6 = 9   →  3x = 15  →  x = 5
Cas 2 : 3x - 6 = -9  →  3x = -3  →  x = -1

Ensemble solution : S = {-1 ; 5}

Vérification :
|3×5 - 6| = |9| = 9 ✓
|3×(-1) - 6| = |-9| = 9 ✓
```

### Cas 2 — |ax + b| = |cx + d|

Deux cas : ax + b = cx + d  OU  ax + b = -(cx + d)

```
Résoudre : |x + 2| = |2x - 1|

Cas 1 : x + 2 = 2x - 1   →  x = 3
Cas 2 : x + 2 = -(2x-1)  →  x + 2 = -2x + 1  →  3x = -1  →  x = -1/3

S = {-1/3 ; 3}
```

### Cas 3 — Inégalités avec valeur absolue (compléments)
|ax + b| < c  ⟺  -c < ax + b < c  (si c > 0)
|ax + b| > c  ⟺  ax + b > c  OU  ax + b < -c  (si c > 0)

---

## 5. Cas particuliers importants

### Équation sans solution (équation impossible)

```
Résoudre : 3x + 6 = 3x - 2

3x + 6 - 3x = -2
6 = -2  →  FAUX pour toute valeur de x

Ensemble solution : S = ∅ (aucune solution)
```

### Équation avec une infinité de solutions (identité)

```
Résoudre : 2(x + 3) = 2x + 6

2x + 6 = 2x + 6
0 = 0  →  VRAI pour toute valeur de x

Ensemble solution : S = ℝ
```

**Interprétation :** Vérifier si les deux membres représentent la même expression.

---

## 6. Résolution graphique

L'équation ax + b = cx + d revient à trouver l'intersection de :
- Droite d₁ : y = ax + b
- Droite d₂ : y = cx + d

La solution x₀ est l'abscisse du point d'intersection.
- Droites sécantes → une solution unique
- Droites parallèles → aucune solution (même coefficient directeur, ordonnées à l'origine différentes)
- Droites confondues → infinité de solutions

---

## 7. Points de vigilance — Erreurs classiques à éviter

| Erreur | Exemple incorrect | Correction |
|---|---|---|
| Mauvais signe au passage | 3x + 5 = 8 → 3x = 8 + 5 | 3x = 8 - 5 = 3 |
| Division partielle | 4x + 8 = 20 → x + 8 = 5 | 4x = 12 → x = 3 |
| Fraction sans PPCM | x/2 + x/3 = 5 → 2x = 5 | 5x/6 = 5 → x = 6 |
| Valeur absolue → 1 cas | |2x-3|=5 → x=4 | x=4 ou x=-1 |
| Développement incomplet | 2(x-3)=8 → 2x-3=8 | 2x-6=8 → x=7 |

---

## 8. Exercices types

**Exercice 1 :** Résoudre 5x - 3(x + 2) = 4x - (x + 8)
*Réponse :* 5x - 3x - 6 = 4x - x - 8 → 2x - 6 = 3x - 8 → -x = -2 → x = 2

**Exercice 2 :** Résoudre x/(x-1) - 2/(x+1) = 1  (x ≠ 1 et x ≠ -1)
*Réponse :* Multiplier par (x-1)(x+1) → x(x+1) - 2(x-1) = (x-1)(x+1) → x²+x-2x+2 = x²-1 → -x+2=-1 → x=3

**Exercice 3 :** Résoudre |5x + 2| = |3x - 4|
*Réponse :* 5x+2=3x-4 → x=-3 ; ou 5x+2=-(3x-4) → 8x=2 → x=1/4. S={-3 ; 1/4}