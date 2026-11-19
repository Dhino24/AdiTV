# Ce que j’ajoute au projet
Ces propositions complètent les demandes confirmées. Les ajouts MVP sont recommandés sans retarder la première tranche de lecture ; les évolutions ne sont pas toutes à développer immédiatement.

| Ajout proposé | Utilité concrète | Priorité |
|---|---|---|
| Catégories masquées, réordonnées et pays préférés | Retrouver l’utile dans des milliers de chaînes | MVP |
| Protection des contenus adultes et PIN | Éviter l’ouverture involontaire, usage familial | MVP simple ; profils avancés après |
| Profil d’image/moteur mémorisé par chaîne | Ne pas refaire un réglage qui résout un flux particulier | MVP |
| Diagnostic lisible des coupures | Comprendre ce qui est observé et essayer une action adaptée | MVP |
| Catalogue et guide consultables en cache | Ouvrir vite même quand le fournisseur répond lentement | MVP |
| Corrections manuelles TMDB/EPG persistantes | Éliminer les affiches et programmes mal associés | MVP |
| Accessibilité : texte, sous-titres, contraste, animations réduites | Confort réel sur grand écran | MVP |
| Minuterie de sommeil et arrêt après inactivité configurable | Éviter une lecture ou session inutilement prolongée | MVP léger |
| Historique effaçable et suppression de « Reprendre » | Garder le contrôle de ses données et de l’accueil | MVP |
| Export/restauration des préférences sans mots de passe | Retrouver son organisation après réinstallation | MVP léger |
| Statut abonnement et expiration si exposés | Comprendre pourquoi un accès ne fonctionne plus | MVP conditionnel |
| Déduplication et regroupement de variantes HD/4K | Choisir une version sans quatre affiches identiques | Après MVP ; correspondances vérifiées |
| Profils locaux adultes/enfants | Favoris, historique et préférences séparés | Après MVP ; PIN simple avant |
| Saisie des accès depuis le téléphone via QR code | Éviter de taper un long mot de passe à la télécommande | Après MVP |
| « Ce soir » pour les chaînes favorites | Choisir rapidement une émission | Après MVP |
| Suivi des séries et nouveaux épisodes disponibles | Voir ce qui a réellement été ajouté à l’abonnement | Après MVP |
| Collections/sagas et recherche par acteur | Explorer le catalogue enrichi TMDB | Après MVP |
| Réglages sous-titres/audio mémorisés par profil | Retrouver langue, taille et contraste préférés | Base MVP ; profils après |
| Épingler quelques chaînes sur l’accueil | Accéder directement aux chaînes les plus utilisées | MVP |

## Garde-fous sur les bonus
- QR téléphone : session locale temporaire avec code unique, expiration, confirmation sur TV, HTTPS ou mécanisme assurant la confidentialité et nettoyage immédiat. Une page HTTP avec mot de passe brut sur le LAN n’est pas acceptable. Ne pas exposer un serveur d’administration permanent. Si la sécurité ne peut pas être réalisée simplement, garder la saisie TV pour le MVP.
- Profils : locaux par défaut ; pas de cloud ni compte supplémentaire automatique. Catégories fournisseur ne garantissent pas toutes une classification d’âge correcte.
- Expiration : afficher seulement la valeur fournie, avec fuseau et état inconnu sinon ; pas de renouvellement ou paiement depuis ADITV.
- Collections : uniquement les titres accessibles ; épisodes nouveaux selon le catalogue, pas seulement annoncés par TMDB.
- Minuterie : avertissement annulable, libération de la session et position sauvegardée.
- Sauvegarde : choix explicite, pas de mots de passe, URLs de flux ou tokens inclus.

## Ce que je reporte volontairement
Enregistrement, téléchargement vidéo, multi-écran, lecture simultanée, multiconnexion « accélératrice », cloud obligatoire, assistant IA et monétisation. Ces fonctions ajoutent coûts, contraintes ou complexité avant que l’expérience de lecture soit stabilisée.

## Ordre conseillé
D’abord rendre la connexion, la lecture et le focus impeccables. Ensuite EPG, métadonnées et diagnostic. Puis polish et petits ajouts de confort. Les profils avancés et le QR viennent après les mesures sur la Stick réelle.
