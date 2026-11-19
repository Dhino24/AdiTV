# PROJECT_STATE — ADITV
_Mis à jour : 19/11/2026_

## Appareil cible (confirmé)
Fire TV Stick 4K **1re génération**, Fire OS **6.7.1.1** = Android 7.1 (API 25), ARMv7 32 bits, ~1 Go de stockage libre.
Codecs/HDR annoncés par Amazon : **à mesurer**, rien n'est supposé acquis. Le lint bloque tout appel d'API postérieur à Android 7.1.

## Phase actuelle : 3 — terminée côté code (0.3.0), à valider sur la Stick
0.2.0 installée par Adama (installation OK).

### Correctif 0.3.1 (retour d'Adama : « films et séries vides »)
- Panneaux Xtream récents : `category_id` vide, seulement `category_ids` → titres importés mais rattachés à aucune catégorie. Lecture de `category_ids` en repli (testé).
- Entrée « Tous les films / Toutes les séries / Toutes les chaînes » en tête de chaque écran : tout reste accessible même sans catégorie exploitable.
- Délai de lecture des listes porté à 240 s (serveurs lents à générer les gros catalogues).
- Resynchronisation automatique unique au premier démarrage de la 0.3.1. **Cause exacte encore à confirmer** sur l'abonnement d'Adama (Paramètres → Catalogue local).

### Livré en phase 3
- **Version française et qualité max mises en avant** (demande d'Adama) : les variantes d'un même contenu chez le fournisseur (VF, VOSTFR, MULTI, SD/HD/FHD/4K, préfixes pays, catégories « VOSTFR »…) sont regroupées en **une seule carte** ; la meilleure selon les préférences est affichée et lue (par défaut : français d'abord, qualité maximale). Chaînes : même pays + même nom = même chaîne (« BE| RTL » ≠ « FR| RTL »). Films : même titre + même année (un remake reste distinct).
  - Réglages (Paramètres → Lecture) : VF / VOSTFR / VO d'abord ; 4K / 1080p / 720p max ; flux adaptatifs « débit max » (défaut) ou « adapté au réseau ».
  - Fiche : « Versions disponibles (annoncées par le fournisseur) » ; lecteur : bouton **Version** pour changer en cours de lecture (position conservée).
  - Lecteur : audio français préféré quand le flux en contient ; sous-titres français préférés pour une version VOSTFR.
  - Langue et qualité viennent du **titre fournisseur** (jamais de TMDB) et sont affichées « annoncées ».
- **Direct** : favoris (★ dans le lecteur et sur la fiche chaîne), catégorie « ★ Favoris », rangée « Chaînes favorites » sur l'accueil ; **zapping ↑/↓** (et touches chaîne) dans la liste d'origine (favoris, catégorie ou toutes), une seule session ; bandeau chaîne avec maintenant / ensuite et progression ; programme en cours sous chaque chaîne.
- **Guide TV (EPG)** : XMLTV du fournisseur (`xmltv.php`, URL reconstruite avec les accès chiffrés) + sources XMLTV https ajoutées ; lecture en flux, gzip, limite 400 Mo, DTD/entités externes non traitées ; horaires stockés en UTC, changement d'heure géré, horaires sans fuseau → fuseau du serveur (comptés et affichés) ; fin manquante déduite du programme suivant seulement, sinon ignorée ; fenêtre −1/+7 jours ; remplacement seulement après import complet ; une seule source par chaîne (fournisseur prioritaire) ; décalage par source et par chaîne ; **association manuelle** (fiche chaîne → Guide), « pas de guide » ; couverture affichée. Repli « maintenant / ensuite » via `get_short_epg`. Actualisation ~12 h, jamais pendant une lecture.
  - **Grille télécommandable** : chaînes en lignes (virtualisées), axe horaire commun qui suit le focus, repère « maintenant », filtres Favoris / Toutes / catégories, Maintenant / −6 h / +6 h / jour suivant ; panneau programme (regarder, fiche chaîne).
  - **Replay** : visible seulement si le fournisseur déclare une archive (`tv_archive`) couvrant l'émission ; schéma `timeshift` Xtream **expérimental**, à valider avec une émission autorisée.
- **Bandes-annonces** : bouton « Bande-annonce » sur la fiche si TMDB référence une vidéo YouTube (française d'abord, sinon anglaise) **et** si l'application YouTube (ou un navigateur) est installée sur la Stick ; sinon pas de bouton.
- **Base v3** : migration 1→2→3 testée (favoris, guide, colonnes de variantes recalculées au démarrage).
- **60 tests** : variantes et préférences, XMLTV (offsets, été/hiver, fins manquantes, XXE, gzip, taille, fenêtre), dépôt du guide (priorité, décalages, association manuelle, panne sans perte), API courte et URL de replay, migration v3, parcours télécommande complet (Direct, Guide, panneau programme, Retour système) en API 25 et 35.

### Réel / simulé
| Élément | État |
|---|---|
| Lecture film / épisode / direct, zapping | Code complet, session unique testée — **lecture réelle à confirmer par Adama** |
| Guide fournisseur (XMLTV / short EPG) | Testé sur données simulées — **taille et temps d'import à mesurer sur la Stick** |
| Replay | Schéma Xtream courant implémenté — **expérimental, jamais testé** |
| TMDB | Testé contre un serveur simulé — **jamais avec une vraie clé** |
| Interface | Testée en JVM (Robolectric) — fluidité réelle à juger sur la Stick |

### Limites connues
- Bandes-annonces : ouvertes dans l'application YouTube, pas de lecture intégrée ni d'aperçu automatique en fond (non recommandé sur la Stick 1re génération ; à mesurer avant d'envisager).
- Pas de choix de moteur (LibVLC, phase 4). Favoris et reprise sont liés à la version choisie, pas à toutes les versions.
- La grille charge les programmes de toutes les chaînes du filtre « Toutes » : à mesurer avec un gros bouquet.
- Une catégorie par élément ; chaque catégorie chargée en entier (à mesurer sur un gros catalogue).
- Les journaux internes de Media3 sont coupés en release ; en debug ils restent actifs (niveau avertissement).

## À faire par Adama
- [x] 0.2.0 installée.
- [x] 0.3.0 installée : films et séries vides signalés → 0.3.1.
- [ ] Installer `ADITV-0.3.1-debug.apk`, attendre la resynchronisation, vérifier Films / Séries et relever Paramètres → Catalogue local.
- [ ] Lire un film, un épisode, une chaîne ; zapper ↑/↓ ; ouvrir le Guide TV et noter la couverture affichée et le temps d'actualisation.
- [ ] Optionnel : créer une clé TMDB et la saisir dans Paramètres → Métadonnées TMDB.

## Prochaine étape : phase 4
LibVLC (ARMv7) derrière le même contrat de lecture, choix Auto / Media3 / VLC mémorisé par contenu, bascule bornée sur erreur de format, diagnostic de lecture (débits, réserve, interruptions, décodeur) avec états mesuré / estimé / N/D.
