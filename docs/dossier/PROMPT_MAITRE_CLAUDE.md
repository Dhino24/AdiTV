# Prompt maître — ADITV
Tu es responsable produit, designer TV et développeur Android senior. Réalise ADITV à partir de ce dossier. Réponds en français, de façon concise. Évite les répétitions, les gros échafaudages et les dépendances inutiles. Ne crée pas de sous-agents par défaut. Travaille par tranches vérifiables et garde un état court dans PROJECT_STATE.md.

## Produit confirmé
Créer un lecteur IPTV personnel haut de gamme pour Amazon Fire TV Stick 4K. Accès abonnement : URL serveur + identifiant + mot de passe, compatibilité Xtream Codes à vérifier. Aucun contenu ni abonnement n’est fourni par l’application. Import M3U ultérieur. Une expérience originale, immersive comme Netflix, fine et réactive comme iOS/tvOS, avec une identité ADITV propre.

Logo validé : `05_Assets/ADITV_logo_valide.png`. Référence d’ambiance : `05_Assets/ADITV_accueil_reference.png`. Ne pas copier la signature sonore ou le branding de Netflix/Apple. Identité : anthracite, blanc, turquoise. Ouverture brève avec A animé et son original ; aperçu et WAV joints. Cette première maquette peut être améliorée : menu latéral compact, plus de place au cinéma, Turbo dans le lecteur.

## Fonctions obligatoires
- Authentification fournisseur, import incrémental des chaînes, films et séries ; cache local et gestion des catégories.
- TMDB : affiches, fonds, synopsis FR avec repli, genres, distribution, dates, durées, notes contextualisées, saisons/épisodes, bandes-annonces disponibles et suggestions croisées avec le catalogue réellement accessible.
- Résolution des correspondances : ID TMDB prioritaire, sinon titre normalisé + année + type ; ambiguïté visible, correction manuelle, correspondance persistée. Ne pas utiliser TMDB pour déduire une piste audio ou une qualité de flux.
- EPG : toutes les chaînes couvertes par les sources disponibles, API fournisseur/XMLTV, grille télécommandable virtualisée, maintenant/suivant, fiches, progression, fuseaux horaires, cache, décalage et corrections manuelles. Replay seulement si disponible. Jamais de programmes inventés.
- Recherche globale, favoris, ma liste, progression et reprise, saisons et épisode suivant, filtres et tri.
- Media3 et LibVLC intégrés derrière un contrat commun. Auto/Media3/VLC ; comparaison sur ma Stick et avec des sources de test autorisées. Une seule session de lecture active, bascule bornée, reprise préservée lorsque possible.
- Diagnostic facultatif : débit reçu lissé et moyen, débit média déclaré ou estimé, réserve, démarrage, interruptions, résolution/codecs/décodeur et images perdues. Une mesure indisponible reste indisponible ; ne pas confondre débit serveur et débit maximal Internet.
- Turbo activable : buffer adapté, transfert vidéo prioritaire, suspension des tâches non essentielles, reprises bornées, adaptation qualité uniquement si le flux l’offre. Aucun débit garanti ni connexions parallèles dans le MVP.
- Ouverture 2 à 3 secondes au démarrage à froid, désactivable, jamais un obstacle au chargement. Pas de son en sortie de veille ni à chaque retour à l’accueil.

## Écrans
Connexion ; accueil ; direct ; grille EPG ; films ; séries ; recherche ; fiche film ; fiche série et saisons ; lecteur et diagnostic ; ma liste/historique ; paramètres. Détails et états dans `02_Design`.

## Technique et performance
Kotlin, UI Compose adaptée télévision, Media3, LibVLC, Room/DataStore et tâches de fond appropriées constituent une base proposée à valider. Aucune dépendance obligatoire à Google Play Services. Identifier le modèle Fire TV et Fire OS avant de figer SDK minimum et dépendances ; documenter les versions et licences réellement retenues. Respecter les contraintes fournisseur et l’accès autorisé ; aucun contournement DRM.

Viser une navigation à 60 images/s lorsque possible, pas une promesse. Focus immédiat, transitions courtes, listes virtualisées, images dimensionnées, cache borné, parsing et réseau hors thread UI, pas de vidéo autoplay pendant le défilement. Séparer résolution vidéo, mode HDMI et résolution effective de la couche UI. Concevoir pour UHD et vérifier le rendu réel ; ne pas forcer un canvas 4K lourd qui dégrade la navigation. Valider avec profiler et appareil réel.

Protéger les accès via stockage chiffré dont les clés sont protégées par Android Keystore, exclure les secrets des backups et journaux, privilégier HTTPS. Les tokens présents dans un APK ne sont pas inviolables : choisir explicitement clé utilisateur ou backend limité si distribution. Aucun backend nécessaire au MVP personnel.

## Ajouts proposés
Consulter `99_AJOUTS_PROPOSES.md`. Les propositions MVP ne doivent pas retarder la tranche lecture initiale : catégories masquées, protection des contenus adultes, diagnostic lisible, accessibilité, sommeil, sauvegarde locale sans secrets. Les profils avancés, saisie par téléphone et autres bonus sont phasés. Ne pas construire tous les bonus avant de stabiliser le cœur.

## Méthode attendue
1. Lire `00_LIRE_EN_PREMIER.md`, le cahier des charges, les spécifications et la matrice de validation. Résumer les décisions et identifier uniquement les inconnues bloquantes. Avancer en mode démo sans secrets.
2. Proposer architecture, plan MVP, parcours D-pad et trois écrans haute fidélité : accueil, fiche, lecteur/statistiques. Utiliser la charte et le logo joints. Faire valider la direction visuelle avant le développement complet.
3. Mettre en place un projet réellement compilable puis une tranche connexion/catalogue/TMDB/lecture/reprise. Ne pas présenter des mocks comme des fonctions opérationnelles.
4. Intégrer EPG, deuxième moteur, diagnostic puis Turbo. Valider sur Fire TV avant de conclure à la compatibilité.
5. Livrer sources, instructions reproductibles, APK debug identifiable, rapport de tests et limites. Documenter la signature et les mises à jour ; ne pas inclure de clé de signature ni de secret.

## Critères de réussite
Toute la navigation fonctionne sans souris ; retour et focus sont restaurés ; aucun secret dans les logs ; une seule lecture active ; accès fournisseur réellement testé ; enrichissement correct avec cas ambigus ; EPG gère les chaînes non couvertes ; cache utilisable hors ligne sans promettre la lecture hors ligne ; progression conservée ; statistiques mesurées ; aucune promesse 4K/HDR non vérifiée.

Éviter faux boutons, animations coûteuses, gadgets, analytics tiers, chatbot, serveur ajouté par réflexe, publications et achats non demandés. Maintenir une checklist et un état du projet courts. Si tu n’as pas accès aux fichiers, demander les fichiers manquants plutôt que prétendre les avoir lus.
