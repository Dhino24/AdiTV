# Écrans, interactions et états
| Écran | Contenu essentiel | Actions principales |
|---|---|---|
| Connexion | Serveur, utilisateur, mot de passe masqué, test | Saisir, afficher momentanément, tester, enregistrer |
| Accueil | Hero, reprendre, favoris, ajouts, suggestions | Déplier menu, ouvrir fiche, reprendre |
| Direct | Catégories, chaînes, maintenant/suivant | Lire, favori, ouvrir EPG |
| EPG | Chaînes verticales, heures horizontales | Maintenant, jour, détail, lire/replay conditionnel |
| Films | Grille, catégories, tri, filtres | Fiche, liste, recherche |
| Séries | Grille, progression | Fiche, saisons, reprendre |
| Fiche film | Affiche, fond, résumé, cast, note/votes | Lire, liste, bande-annonce si disponible |
| Fiche série | Métadonnées, saisons, épisodes accessibles | Reprendre, épisode, liste |
| Recherche | Saisie, récents, résultats par type | Film, série, chaîne, programme |
| Lecteur | Vidéo, transport, pistes, réglages | Pause, seek VOD, chaînes direct, Turbo, diagnostic |
| Ma liste | Films/séries suivis, favoris séparés | Lire, retirer, filtrer |
| Paramètres | Compte, moteur, EPG, cache, intro, confidentialité | Modifier, tester, effacer sélectivement |

## Télécommande
D-pad déplace le focus spatialement. OK active. Retour ferme d’abord le panneau, puis revient à l’écran précédent ; à la racine, sortie maîtrisée sans bloquer l’utilisateur. Lecture/pause agit sur la vidéo ; en VOD, droite/gauche modifient la position lorsque la barre est active. En direct, ne pas détourner ces touches vers un seek inexistant. Accès à la liste et au guide explicitement visible. Appuis longs et raccourcis sont des bonus, jamais l’unique accès. Les touches système restent au système.

## États indispensables
Chargement initial avec avancement utile ; rafraîchissement discret ; catalogue vide ; erreur auth ; connexion expirée ; limite de sessions si signalée ; réseau perdu ; serveur lent ; format non supporté ; EPG absent/périmé ; correspondance TMDB ambiguë ; image manquante ; accès parental ; espace faible. Ne pas prétendre identifier précisément une cause serveur sur la seule base d’un timeout.

## Continuité
Avant lecture : enregistrer écran et focus. Pendant lecture : progression périodique et aux événements de cycle de vie. Après lecture : restaurer contexte. Fin d’épisode : suggestion du suivant annulable ; préférence autoplay. Mise en veille : pause/libération conforme au cycle de vie, aucune deuxième session au réveil. Catalogue en cache consultable sans réseau, mais vidéo hors ligne non promise.
