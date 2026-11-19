# Cahier des charges
## Décisions confirmées
Nom ADITV ; usage personnel ; premier support Fire TV Stick 4K ; accès URL/utilisateur/mot de passe ; catalogue fournisseur enrichi TMDB ; EPG pour toutes les chaînes couvertes ; Media3 et LibVLC ; statistiques et Turbo ; UI cinéma premium ; logo anthracite validé ; ouverture avec son original. Aucune publication sur un store demandée.

## Périmètre MVP
Connexion avec test et erreurs utiles ; catalogue persistant incrémental ; accueil et sections ; direct/favoris/EPG ; films et séries ; fiches TMDB ; recherche ; lecteur ; sous-titres et pistes réellement présents ; historique/reprise ; choix moteur ; statistiques ; Turbo ; réglages essentiels. Catalogue accessible pendant l’enrichissement. Ma liste est distincte des chaînes favorites et de l’historique.

L’accueil expose reprendre, favoris, ajouts récents du fournisseur (pas automatiquement sorties récentes), catégories sélectionnées et suggestions disponibles. Pas de popularité fabriquée. Les recommandations TMDB absentes du catalogue ne portent pas un bouton Lire ; une future section découverte doit les distinguer explicitement.

## Capacités conditionnelles
Replay/timeshift/qualité adaptative/sous-titres/HDR/4K/audio multicanal dépendent du flux, fournisseur, décodeur, Fire TV, TV et HDMI. Masquer ou désactiver avec explication les fonctions indisponibles. Timeshift n’est pas une conséquence automatique de l’EPG. Aucun téléchargement hors ligne ni enregistrement dans le MVP.

## Données et fonctionnement
Catalogues, EPG et images se chargent séparément. Dernier catalogue utilisable immédiatement ; mise à jour en arrière-plan avec date. Un changement de fournisseur ne réutilise jamais les IDs bruts d’un ancien compte. Redémarrage après fermeture et perte réseau prévus. Pas de lecture automatique déclenchant plusieurs sessions.

## À confirmer sur l’appareil
Modèle/génération exacts, Fire OS, résolution UI, modes HDMI, mémoire disponible, codecs décodés matériellement, prise en charge audio et comportement des touches multimédias. Connexion Xtream à vérifier sans déduire la compatibilité du seul format d’identifiants.

## Hors périmètre initial
Distribution publique, abonnement vendu par ADITV, accès à des sources nouvelles, DRM contourné, cloud obligatoire, multiconnexion pour augmenter le débit, mode hors ligne vidéo. Les extensions proposées sont listées en fin de dossier.
