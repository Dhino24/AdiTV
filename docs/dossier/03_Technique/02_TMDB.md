# Intégration TMDB
## Données
Titre FR/original, résumé FR avec repli, affiche/fond, date, genres, durée si disponible, cast/équipe, note avec nombre de votes et source TMDB, saisons/épisodes, vidéos/bandes-annonces et collections lorsque présents. L’API fournit des métadonnées et liens, pas les films à lire. Garder les capacités du flux distinctes.

## Matching
1. Vérifier ID TMDB et type fournis avant de l’accepter.
2. Conserver le titre brut ; créer un titre de recherche nettoyé (préfixes pays, qualité, VOSTFR…) sans supprimer des mots significatifs.
3. Chercher film ou série avec année si fiable et variantes du titre.
4. Score multi-signal titre/original/année/type ; seuils à calibrer sur échantillon représentatif. Ne pas auto-valider systématiquement le premier résultat.
5. Si ambigu : données fournisseur, indication interne, correction manuelle. Préserver le choix manuel après synchronisation.
6. Associer saisons/épisodes avec numérotation vérifiée ; ne pas créer des épisodes absents du fournisseur. Gérer épisodes spéciaux et numérotations non standard sans les deviner.

## Réseau
Authentification application par token ou clé, configuration images officielle, tailles adaptées, cache avec fraîcheur et conditions API, déduplication des demandes, regroupement append_to_response lorsque pertinent. Backoff sur 429/5xx et aucune boucle infinie. En cas de panne TMDB, lecture et catalogue restent utilisables. Les bandes-annonces ont leur propre disponibilité ; ne pas promettre une URL vidéo directe universelle.

## Recommandations
Intersecter TMDB similaires/collections avec les identifiants confirmés du catalogue. Suggestions locales simples par genres/historique, pas de modèle IA obligatoire. Ne jamais appeler une note TMDB « note de l’abonnement » ou une catégorie fournisseur « tendance mondiale ».

## Accès et attribution
API utilisateur personnelle configurable ; ne pas embarquer un secret commun supposé impossible à extraire. Si distribution : évaluer proxy avec quotas et coûts avant de l’ajouter. Afficher logo approuvé TMDB et mention : “This product uses the TMDB API but is not endorsed or certified by TMDB.” Vérifier exigences et licence avant publication. Usage non commercial gratuit avec attribution selon FAQ consultée ; usage commercial à requalifier. Aucun logo TMDB officiel n’a été redessiné ni fourni dans ce ZIP.
