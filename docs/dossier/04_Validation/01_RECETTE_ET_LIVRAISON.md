# Recette et livraison
## Scénarios minimaux
Connexion valide/invalide, serveur indisponible/lent, expiration et limite signalée ; gros catalogue ; M3U seulement après MVP. Matching homonymes/remakes/titres VOSTFR ; TMDB en panne/429 ; corrections manuelles préservées. EPG sans source, trou, doublon régional, offsets, changement heure Europe/Paris et source Afrique/Dakar. Replay absent/présent. Flux autorisés HLS et TS et VOD représentatifs réellement fournis ; pistes, sous-titres, erreurs décodeur et transitions réseau.

## Télécommande et cycle de vie
Parcours entier sans tactile ; retour/focus de chaque panneau ; appuis répétés/maintenus ; fin d’épisode ; éteindre/réveiller ; perte réseau/reconnexion ; fermer et relancer ; pas de deuxième session au changement moteur. Préférences persistantes et effacement sélectif. Intro courte, son désactivable, pas d’intro sur simple reprise.

## Mesures
Profiler sur Stick exacte : démarrage froid/chaud, frame times et pourcentage frames lentes, mémoire avant/après 30 min et zapping, cache disque, temps première image, interruptions et réseau. Réaliser un scénario reproductible de 20 déplacements + ouverture fiche + lecture, puis une session longue. Les objectifs fluidité ne deviennent « validés » qu’avec mesures. Noter modèle, OS, version app, type source et configuration TV sans secrets.

Comparaison normal/Turbo : même contenu ou séquence de test équivalente, même appareil et conditions ; plusieurs essais si réseau variable. Publier temps de départ, nombre/durée coupures, réserve et mémoire. Si Turbo ne fait pas mieux, le signaler. Contrôle résolution vidéo effective vs UI/HDMI ; badge 4K/HDR seulement si justifié.

## Livraison
Build reproductible sur machine de développement ; APK debug installable, non fourni dans le présent dossier ; checksum ; instructions d’installation avec activation développeur ponctuelle et désactivation du debug après usage si souhaitée ; méthode mise à jour conservant données via même signature ; pas de clé de signature dans le ZIP public. Journal des limites et matrice des fonctions réelles/simulées. Vérifications automatisées ciblées : parsing catalogue/EPG/temps, matching, progression et politique de bascule/retry. Vérification manuelle indispensable pour UX et codecs.

## État de ce dossier
Documents et démonstration locale préparés ; archive et références internes contrôlées. Aucun APK ni test matériel ou abonnement réel exécuté.
