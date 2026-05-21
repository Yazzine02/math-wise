# Systèmes d'équations — Cours complet
# Niveau : Terminale (Bac général spécialité maths)
# knowledge_node_code : EQ_SYSTEMS

---

## 1. Définition

Un système de deux équations à deux inconnues x et y est de la forme :

```
{ ax + by = e
{ cx + dy = f
```

**Résoudre** le système : trouver tous les couples (x, y) vérifiant simultanément les deux équations.

---

## 2. Méthode 1 — Substitution

**Principe :** Exprimer une inconnue en fonction de l'autre dans une équation,
puis substituer dans la deuxième.

**Algorithme :**
1. Dans l'équation la plus simple, exprimer x en fonction de y (ou y en fonction de x)
2. Substituer cette expression dans l'autre équation → équation à une seule inconnue
3. Résoudre
4. Remonter pour trouver l'autre inconnue
5. Vérifier dans les DEUX équations originales

```
Résoudre :
{ x + 2y = 7       (1)
{ 3x - y = 7       (2)

Étape 1 — De (1) : x = 7 - 2y

Étape 2 — Substituer dans (2) :
3(7 - 2y) - y = 7
21 - 6y - y = 7
21 - 7y = 7
-7y = -14
y = 2

Étape 3 — Remonter :
x = 7 - 2×2 = 3

Solution : (x, y) = (3, 2)

Vérification :
(1) : 3 + 2×2 = 7 ✓
(2) : 3×3 - 2 = 7 ✓
```

---

## 3. Méthode 2 — Combinaison linéaire (élimination)

**Principe :** Multiplier les équations par des coefficients pour faire disparaître une inconnue.

**Algorithme :**
1. Choisir l'inconnue à éliminer
2. Multiplier chaque équation par un coefficient approprié
3. Additionner les équations obtenues → élimine une inconnue
4. Résoudre l'équation à une inconnue
5. Remonter pour trouver l'autre inconnue

```
Résoudre :
{ 2x + 3y = 12     (1)
{ 5x - 2y = 1      (2)

Objectif : éliminer y
Multiplier (1) par 2  :  4x + 6y = 24   (1')
Multiplier (2) par 3  : 15x - 6y = 3    (2')

Additionner (1') + (2') :
19x = 27
x = 27/19

Remonter dans (1) :
2×(27/19) + 3y = 12
3y = 12 - 54/19 = (228-54)/19 = 174/19
y = 58/19

Solution : (x, y) = (27/19 ; 58/19)
```

**Astuce :** Pour éliminer x, trouver le PPCM des coefficients de x et ajuster les signes.

---

## 4. Cas particuliers

### Système sans solution (lignes parallèles)

```
{ 2x + y = 3       (1)
{ 4x + 2y = 7      (2)

(2) = 2×(1) à gauche mais 7 ≠ 2×3 = 6
→ Contradiction : 2×(1) donne 4x+2y=6, mais (2) dit 4x+2y=7
→ Système incompatible : S = ∅
```

Interprétation géométrique : deux droites parallèles (même pente, ordonnées différentes).

### Système avec infinité de solutions (droites confondues)

```
{ x + 2y = 4       (1)
{ 2x + 4y = 8      (2)

(2) = 2×(1) à gauche ET à droite
→ Les deux équations sont identiques
→ Infinité de solutions : tous les (x, y) vérifiant x + 2y = 4
→ Paramétrer : y = t ∈ ℝ, x = 4 - 2t
```

---

## 5. Systèmes non linéaires (fréquents en Terminale)

### Système avec une équation du 2ème degré

```
{ x + y = 3        (1)
{ x² + y² = 5      (2)

De (1) : y = 3 - x

Substituer dans (2) :
x² + (3-x)² = 5
x² + 9 - 6x + x² = 5
2x² - 6x + 4 = 0
x² - 3x + 2 = 0
(x-1)(x-2) = 0  →  x = 1 ou x = 2

x = 1 → y = 2  ;  x = 2 → y = 1

Solutions : (1, 2) et (2, 1)
```

### Système "somme × produit" (très classique au bac)

Trouver deux nombres connaissant leur somme S et leur produit P :
ils sont racines de X² - SX + P = 0

```
Deux nombres ont pour somme 7 et pour produit 12. Les trouver.

X² - 7X + 12 = 0
(X-3)(X-4) = 0  →  X = 3 ou X = 4
Les deux nombres sont 3 et 4.
```

---

## 6. Interprétation géométrique

Un système 2×2 correspond à l'intersection de deux droites dans le plan :

- **Unique solution** : droites sécantes (se coupent en un point)
- **Aucune solution** : droites parallèles (même coefficient directeur, ordonnées différentes)
- **Infinité de solutions** : droites confondues (même équation)

La solution (x₀, y₀) est le point d'intersection.

---

## 7. Systèmes 3×3 (compléments)

```
{ x + y + z = 6    (1)
{ 2x + y - z = 1   (2)
{ x - y + 2z = 5   (3)

Méthode : élimination successive
(1)+(2) : 3x + 2y = 7      (4)
(1)+(3) : 2x + 3z = 11     (erreur, recommencer)

(2)+(3) : 3x + z = 6       (5)
(1)+(3) : 2x + 3z = 11     (6)

De (5) : z = 6 - 3x
Substituer dans (6) : 2x + 3(6-3x) = 11  →  -7x = -7  →  x = 1
z = 6-3 = 3
De (1) : 1+y+3 = 6  →  y = 2

Solution : (1, 2, 3)
```

---

## 8. Erreurs classiques

| Erreur | Description | Correction |
|---|---|---|
| Oubli de vérification | Valeur trouvée non testée dans les deux équations | Toujours vérifier dans (1) ET (2) |
| Substitution partielle | Remplacer x dans une seule équation | Bien substituer dans L'AUTRE équation |
| Erreur de signe dans la combinaison | Additionner au lieu de soustraire | Vérifier les coefficients et signes |
| Paramétrage oublié | Infinité de solutions → donner un seul couple | Exprimer en fonction d'un paramètre t |
| Non-linéaire → méthode linéaire | Appliquer la combinaison sur x²+y² | Utiliser la substitution |