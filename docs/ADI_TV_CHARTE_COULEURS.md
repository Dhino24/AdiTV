# ADI TV — Charte couleurs & rendu premium

**Objectif :** harmoniser l'identité visuelle d'ADI TV autour d'un rendu **cinématographique, sobre, profond et haut de gamme**, sans modifier inutilement la structure fonctionnelle de l'application.

## 1. Diagnostic : problème principal de l'interface actuelle

L'interface actuelle donne parfois une impression **trop « bleu tech »** ou **trop IPTV**.

Ce qui réduit la perception premium :
- Trop de **bleu vif**, particulièrement dans les sections **Direct** et **Sport**.
- Trop d'éléments lumineux simultanément.
- Une hiérarchie visuelle insuffisante entre **fond**, **focus**, **actions** et **informations secondaires**.
- Des surfaces parfois trop plates ou au contraire visuellement chargées.

> Le rendu premium ne vient pas de plus de couleurs, mais de **moins de couleurs, mieux utilisées**.

## 2. Palette premium ADI TV

| Rôle | Couleur | Code hexadécimal | Usage |
|---|---|---|---|
| Fond principal | Noir bleuté profond | `#07111A` | Arrière-plan global de l'application |
| Fond alternatif | Noir bleuté doux | `#0B1520` | Variantes de fonds et transitions |
| Surface 1 | Bleu nuit discret | `#101C28` | Cartes, panneaux et overlays |
| Surface 2 | Bleu nuit intermédiaire | `#162433` | Éléments surélevés |
| Surface 3 | Bleu nuit clair | `#1B2B3B` | Surfaces de différenciation ponctuelles |
| Accent principal | Turquoise premium | `#22D3C5` | Focus, sélection, progression, interactions actives |
| Accent lumineux secondaire | Cyan doux | `#7EE7F2` | Halo très discret et détails de focus |
| Texte principal | Blanc cassé | `#F4F7FA` | Titres et informations principales |
| Texte secondaire | Gris bleuté clair | `#C3CED8` | Descriptions et informations secondaires |
| Texte tertiaire | Gris bleuté | `#7F93A6` | Aides et informations moins prioritaires |
| Succès | Vert doux | `#26C281` | Confirmation et état positif |
| Alerte / Live | Corail doux | `#FF6B57` | Badges « En direct » et alertes pertinentes |
| Accent spécial | Or discret | `#E0B75F` | Badge exceptionnel, usage très rare |

### Pourquoi ces choix ?

- Le **noir bleuté profond** est plus nuancé qu'un noir pur et donne de la profondeur aux affiches et aux vidéos.
- Le **turquoise** sert à orienter l'attention ; il ne doit pas devenir une couleur de remplissage omniprésente.
- Le **blanc cassé** améliore l'élégance et évite l'agressivité d'un blanc pur partout.
- Le **rouge corail** signale le direct ou les alertes sans saturer tout l'écran.
- Le **doré** reste exceptionnel afin de conserver son caractère distinctif.

## 3. Répartition recommandée des couleurs

**Cible indicative :**

| Catégorie | Proportion |
|---|---:|
| Fonds sombres noir bleuté | **75 %** |
| Surfaces, cartes et panneaux | **15 %** |
| Typographie | **7 %** |
| Turquoise et autres accents / états | **3 %** |

Ces proportions sont une **direction artistique**, pas une règle de calcul au pixel près.

À surveiller :
- Trop de turquoise → rendu « gaming » ou tableau de bord.
- Trop de noir pur → manque de relief et de profondeur.
- Trop d'éléments brillants → disparition de la hiérarchie visuelle.

## 4. Modifications concrètes par écran

### Accueil

**À conserver**
- Le grand visuel *hero* cinématographique.
- L'ambiance sombre et immersive.

**À améliorer**
- Donner plus de profondeur au fond.
- Simplifier les boutons et réduire les effets lumineux.
- Réserver le turquoise au **focus**, aux **actions ou sélections actives** et aux **petits repères de navigation**.

**Bouton principal**
- Fond blanc cassé `#F4F7FA`.
- Texte noir profond `#07111A`.
- Ombre très légère.
- En focus : contour turquoise fin et discret.

**Bouton secondaire**
- Fond sombre légèrement transparent.
- Contour fin.
- Texte clair.
- Focus visible mais sans halo agressif.

### Direct

**Problème à corriger :** trop d'aplats bleus saturés et de blocs visuellement génériques.

**Modifications**
- Remplacer les grands fonds bleus vifs par des surfaces `#101C28` / `#162433`.
- Préserver un espace suffisant autour des logos des chaînes.
- Utiliser des **barres de progression turquoise fines**.
- Réserver un **badge « EN DIRECT »** discret aux programmes réellement en direct.

**Badge « EN DIRECT »**
- Fond corail `#FF6B57`.
- Texte blanc.
- Taille compacte, coins arrondis.
- Aucun effet clignotant ou excessivement lumineux.

### Sport

**Problème à corriger :** aspect trop proche d'un tableau de résultats ou d'un dashboard.

**Modifications**
- Uniformiser le fond sombre.
- Donner aux cartes de match une profondeur subtile sans les saturer de couleurs.
- Utiliser le turquoise pour le focus et les actions sélectionnées.
- Garder le rouge pour les alertes et informations de direct.
- Faire ressortir les scores par la **taille**, l'**espacement** et la **graisse typographique**, plutôt que par plusieurs couleurs concurrentes.

## 5. Techniques pour obtenir un rendu premium

### À ajouter

- Dégradés sombres **très légers** dans les fonds.
- Halo turquoise **diffus et limité aux éléments actifs**.
- Overlays sombres progressifs sur les grands visuels, afin de préserver la lisibilité.
- Séparation des surfaces par des variations de contraste, plutôt que par des bordures épaisses.

### À éviter

- Contours épais partout.
- Bleu néon ou cyan saturé sur de grandes surfaces.
- Ombres lourdes et brillances artificielles.
- Dégradés trop visibles.
- Effets « glow gaming ».

**Qualités recherchées :** *mat, profond, cinématographique, respirant, précis*.

## 6. Signature visuelle ADI TV

Mots-clés à transmettre à Claude ou Codex :

**Noir bleuté profond · Contraste maîtrisé · Turquoise discret · Blanc cassé élégant · Focus lumineux fin · Surfaces superposées · Sobriété haut de gamme · Rendu cinéma.**

L'identité recherchée doit être moderne et distinctive, sans copier les codes exacts d'une autre marque.

## 7. Mini-checklist d'application

### À faire

- [ ] Appliquer un fond global noir bleuté cohérent à tous les écrans.
- [ ] Remplacer les surfaces bleu vif par des niveaux de surface sombres.
- [ ] Réserver le turquoise au focus, à la sélection et aux interactions importantes.
- [ ] Hiérarchiser les textes en trois niveaux de couleur.
- [ ] Uniformiser les états des boutons et les cartes.
- [ ] Utiliser une profondeur très subtile : contraste, overlays, ombres légères.
- [ ] Vérifier le rendu et la lisibilité sur une télévision vue à distance.

### À ne pas faire

- [ ] Ne pas multiplier les accents colorés concurrents.
- [ ] Ne pas employer de bleu vif sur de grandes zones.
- [ ] Ne pas ajouter de glow partout.
- [ ] Ne pas mettre de contours épais autour de chaque élément.
- [ ] Ne pas rendre le rouge omniprésent.
- [ ] Ne pas utiliser du blanc pur pour tous les textes.

## 8. Recommandation finale

La signature visuelle à appliquer est :

> **Noir bleuté profond + turquoise rare + blanc cassé élégant.**

Elle doit faire ressortir les contenus et rendre l'interface **moderne, cinématographique, haut de gamme et agréable à utiliser sur Smart TV**.

**Priorité d'exécution :** commencer par l'uniformisation des couleurs sur **Accueil**, **Direct** et **Sport**, puis appliquer les mêmes règles aux autres sections sans modifier la logique fonctionnelle existante.
