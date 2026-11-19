# ADITV — dossier de conception et de développement
Version 1.0 • 19 novembre 2026 • propriétaire du projet : Adama Fall.

## Intention
Une application personnelle IPTV pour Amazon Fire TV Stick 4K, avec une expérience cinéma et une navigation à la télécommande ambitieuses. Abonnement existant avec URL serveur, identifiant et mot de passe. Métadonnées TMDB, guide TV EPG, Media3 et LibVLC, statistiques de lecture et mode Turbo orienté stabilité.

## Ce que contient ce ZIP
- Un prompt maître autonome prêt à copier dans Claude et des spécifications détaillées.
- Le logo validé sur fond anthracite et la première maquette d’accueil générée.
- Une direction artistique, les parcours, les états d’erreur, le guide d’animation et les objectifs de performance.
- Une démonstration locale d’ouverture avec animation et première proposition sonore synthétisée originale.
- Une planification, une matrice de validation, des exemples fictifs et la liste des ajouts proposés.

## Commencer
1. Décompresser l’archive en conservant les dossiers.
2. Ouvrir `06_Apercu/index.html` dans un navigateur de bureau pour voir la maquette, écouter le son et tester l’ouverture. Cliquer sur le bouton pour autoriser le son.
3. Transmettre tout le dossier à Claude. Coller le texte de `PROMPT_MAITRE_CLAUDE.md` et lui demander de lire les fichiers référencés.
4. Faire réaliser d’abord une tranche fonctionnelle : connexion → catalogue → correspondance TMDB → lecture → reprise. Valider la direction avant de généraliser les écrans.
5. Lire `99_AJOUTS_PROPOSES.md` pour les propositions complémentaires et leur priorité.

## Statut exact des livrables
Ceci est un dossier de conception prêt à développer, pas une application Android terminée. Aucun APK, serveur connecté, identifiant réel ou test Fire TV effectué n’est inclus. La maquette et l’aperçu sont des démonstrations visuelles. Le son WAV est un premier concept original ; le mixage final devra être validé sur téléviseur. Les titres de films sont fictifs. Les images de maquette sont en résolution de génération, pas des exports certifiés 3840 × 2160. Le développement cible la lecture 4K compatible et une interface nette sur écran UHD, avec résolution de rendu vérifiée sur appareil.

## Ordre d’autorité
Les demandes explicites de l’utilisateur priment. Puis : décisions confirmées du cahier des charges, critères de validation, spécifications techniques et exemples. `99_AJOUTS_PROPOSES.md` distingue les propositions des choix déjà validés. Les valeurs de tampon et performances sont des cibles à mesurer, pas des résultats annoncés.

## Inconnues à résoudre sans bloquer la conception
Génération de la Stick, version Fire OS, compatibilité réelle API fournisseur, formats/DRM, limites de sessions, sources EPG, clés API TMDB, capacités TV/HDMI/audio. Ne jamais demander de publier des secrets dans le chat. Aucun service payant ni publication n’est requis pour utiliser ce dossier.
