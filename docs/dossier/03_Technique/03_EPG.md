# EPG : couverture, exactitude et performance
## Objectif
Couvrir toutes les chaînes dont les programmes sont accessibles dans les sources configurées. « Toutes les chaînes » est un objectif de couverture, pas une garantie de données externes. Priorité : source fournisseur puis sources XMLTV ajoutées par l’utilisateur. Couverture visible et chaînes non associées identifiées. Ne jamais télécharger des listes EPG arbitraires ou présenter une source non vérifiée comme universelle.

## Ingestion
Supporter XMLTV XML et compression si présente ; parser en streaming, limiter taille décompressée et durée, désactiver DTD/entités externes. Faire les téléchargements hors thread UI. Exploiter validations HTTP si disponibles. Actualisation initiale proposée 12 h, réglable selon fournisseur ; pas de rafraîchissement complet à chaque zapping. Cache borné à une fenêtre configurable, par exemple -1/+7 jours si les données existent ; nettoyer les lignes obsolètes. API de guide court si nécessaire pour maintenant/suivant.

## Association
ID EPG explicite prioritaire. Noms/variantes comme propositions, jamais preuve suffisante pour homonymes régionaux. Mapping manuel persistant, sélection source prioritaire et règle de fusion explicite ; éviter d’intercaler deux grilles contradictoires. Les chaînes FR, Sénégal et autres pays ont leurs propres fuseaux horaires.

## Temps
Parser les offsets XMLTV ; stocker en UTC, afficher dans le fuseau choisi (Europe/Paris par défaut utilisateur), respecter changements d’heure. Décalage manuel par source/chaîne clairement exprimé. Gestion des programmes chevauchants, trous, fins absentes et programmes passés sans fabrication silencieuse. Si les dates sont ambiguës, signaler et utiliser les règles documentées du fournisseur.

## Interface
Grille virtualisée en lignes et colonnes temporelles ; curseur Maintenant ; jour/heure ; maintenant/suivant dans le lecteur ; fiches avec résumé et visuel si disponibles. Cache indexé : ne pas charger toutes les chaînes × tous les jours dans la mémoire Compose. Couverture et date de dernière mise à jour dans les réglages. Guide consultable sans réseau si cache présent.

## Replay
Visible seulement si le fournisseur expose un mécanisme valide, une fenêtre et une capacité pour cette chaîne. Bouton distinct du direct, retour au direct explicite. EPG seul n’autorise pas pause/seek/replay. Tester les unités d’horodatage, URL et fuseaux avec une émission autorisée.
