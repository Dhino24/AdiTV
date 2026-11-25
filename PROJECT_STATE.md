# PROJECT_STATE — ADITV
_Mis à jour : 20/11/2026_

## Appareil cible (confirmé)
Fire TV Stick 4K **1re génération**, Fire OS **6.7.1.1** = Android 7.1 (API 25), ARMv7 32 bits, ~1 Go de stockage libre.
Codecs/HDR annoncés par Amazon : **à mesurer**, rien n'est supposé acquis. Le lint bloque tout appel d'API postérieur à Android 7.1.

## Phase actuelle : toutes les phases terminées côté code (0.20.0) — recette sur la Stick (`docs/RECETTE.md`)
Films et séries synchronisés et clé TMDB opérationnelle (confirmé par Adama sur la 0.5.0).

### 0.8.7 (21/11/2026) — demande d'Adama : accélérer l'ouverture de l'accueil
- **Top 10** : recherche des titres par l'index (titre exact) au lieu d'une recherche « contient » sur tout le catalogue, jusqu'à 60 fois par type ; mesuré sur 30 000 films (PC) : 749 ms → 45 ms, soit ≈ 16× plus rapide ; résultat gardé en mémoire tant que rien ne change.
- **Rangée de repli « Films »** : la requête qui triait tout le catalogue des films n'est lancée que si le fournisseur ne date pas ses ajouts.
- **Chaînes sport françaises** : expression compilée une fois au lieu d'une par chaîne.
- **Démarrage** : tâches de fond (rappels, matchs, tendances, synchro des chaînes) lancées 12 s après l'ouverture, pour laisser le processeur à l'accueil (immédiates au premier import).

### 0.8.6 (21/11/2026) — rapport d'Adama : la cause est un **blocage de l'interface** (ANR)
- Rapport 0.8.4 : import films/séries réussi (123 s) → correctif 0.8.4 validé. Fermeture = ANR (« signal 3 », traces ANR, touche traitée après 8,4 s), blocages ≈ toutes les 60 s sur l'accueil.
- Cause : « programme en cours » des rangées de chaînes recalculé chaque minute **sur le fil de l'interface** (24 h de programmes par chaîne), plus regroupements de listes (accueil, grilles, Top 10, guide, zapping) sur ce même fil.
- Correctif : tous ces calculs passent en arrière-plan (Dispatchers.Default) ; marge de lecture du guide réduite (12 h → 4 h) ; rafraîchissements recalés sur la minute.
- Rapport : pile du dernier blocage conservée même s'il se débloque juste avant la fermeture ; journal système débarrassé des lignes de GC.

### 0.8.5 (20/11/2026) — proposition d'Adama : films et séries mis à jour toutes les 3 semaines, pas à chaque ouverture
- Films et séries importés automatiquement **une seule fois** (premier import) ; ensuite, **bandeau sur l'accueil toutes les 3 semaines** : « Mettre à jour » ou « Plus tard » (repousse de 3 jours). Bouton manuel toujours dans Réglages → Catalogue local.
- Chaînes inchangées : actualisation automatique (≈ 12 h), légère.

### 0.8.4 (20/11/2026) — 3 rapports d'Adama : fermeture sur l'accueil 30 à 50 s après l'ouverture, mémoire Java basse (13–35 / 192 Mo), 300+ Mo libres
- Piste principale : listes du fournisseur renvoyées en **objet indexé** (`{"123": {...}}`) lues **d'un seul bloc** (des dizaines de Mo) pendant la synchronisation automatique du démarrage → pic soudain entre deux relevés, rapport impossible. Désormais lues entrée par entrée (découpeur JSON en flux, test sur 3 000 films).
- Rapport renforcé : réserve mémoire libérée en cas d'erreur fatale + ligne minimale écrite d'abord ; relevé toutes les 10 s (heure, synchro en cours et nombre reçus) ; début/fin des tâches de fond (synchro, guide, TMDB) ; **surveillance de l'interface** (bloquée > 5 s : pile du fil principal) ; **journal système d'ADITV** joint au rapport suivant (plantage natif, mémoire).

### 0.8.3 (20/11/2026) — Adama : « ça plante au chargement des programmes du Direct, ouvert sur toutes les chaînes »
- **Direct** : ne s'ouvre plus jamais sur « Toutes ». Ouverture sur le **dernier filtre choisi** (mémorisé), sinon Favoris, sinon la **première catégorie française** du fournisseur, sinon la première catégorie.
- « Toutes » reste disponible mais **plafonnée à 200 chaînes** (message : choisir une catégorie pour les autres).
- Un seul chargement de programmes à la fois : changer vite de filtre ou d'horaire annule le précédent.

### 0.8.2 (20/11/2026) — plantage en naviguant dans les menus : mémoire
- **Accueil** : chargeait tout le catalogue des films pour une rangée de 24 → 200 premiers seulement.
- **Films / Séries / Chaînes** : grille **chargée par morceaux** (240 titres, la suite en approchant de la fin) ; tri A → Z / plus récents fait par la base ; total affiché sans charger les titres.
- **Pendant une synchronisation** : listes de l'accueil et des grilles rafraîchies au plus toutes les 2,5 à 3 s (au lieu de chaque paquet reçu).
- **Guide TV** : mis à jour seulement après 90 s sans toucher la télécommande, ou ADITV en arrière-plan (sauf mode démo et actualisation demandée).
- Images inchangées (choix d'Adama).

### 0.8.1 (20/11/2026) — retour d'Adama : « l'appli plante à un moment donné et se ferme toute seule »
- Cause exacte inconnue (pas de journal de la Stick). Corrections des causes probables :
  - **listes à clé en double** (Top 10, rangées de l'accueil, épisodes, titres liés, grilles, guide) : deux éléments identiques renvoyés par le fournisseur ou TMDB faisaient planter Compose → doublons retirés partout ;
  - **tâches de fond** (synchronisation, guide, TMDB, sport) : une erreur imprévue est notée au lieu de fermer l'appli ;
  - **VLC** : plus aucun appel natif après libération du moteur (plantage natif possible en changeant de chaîne ou en quittant) ;
  - **mémoire** : cache d'images vidé dès que Fire OS réclame de la mémoire.
- **Enregistreur de plantages** local : erreur Java (pile d'appels), ou fermeture alors qu'ADITV était à l'écran (plantage natif, mémoire reprise par Fire OS) détectée au lancement suivant, avec les derniers écrans/lectures et l'état mémoire. Réglages → **Plantages** → « Voir sur mon téléphone (QR code) » pour copier le rapport et l'envoyer. Ni URL de flux ni accès dans les rapports.

### 0.8.0 (20/11/2026) — demande d'Adama : « taper les codes facilement »
- **Saisie depuis le téléphone (QR code)** : écran de connexion (bouton « Saisir depuis mon téléphone (QR code) ») et Réglages → TMDB (« Depuis mon téléphone »). La TV affiche un QR code et un code à 4 chiffres ; le téléphone ouvre une page servie **par la Stick elle-même sur le Wi-Fi local** (aucun service Internet), on y tape ou colle les accès, ils arrivent dans les champs ; le focus va sur « Tester la connexion ».
- Sécurité : écoute sur l'adresse locale seulement, port aléatoire, jeton 128 bits dans le QR, code à 4 chiffres pour l'adresse tapée à la main, **5 essais faux = fermeture**, usage unique, fermeture après 10 min, requêtes bornées à 8 Ko, aucune valeur journalisée, page sans script ni ressource externe. Limite assumée : HTTP simple sur le réseau de la maison (dit à l'écran).
- Bibliothèque ajoutée : ZXing core 3.5.3 (Apache 2.0), génération du QR uniquement.

### 0.7.0 (20/11/2026) — phases 4, 5 et 6 (sans code PIN, refusé par Adama)
- **Phase 4 — VLC** : second moteur LibVLC 3.7.7 (ARMv7) derrière un contrat commun ; **Auto** = Media3 puis VLC **une seule fois** si Media3 refuse le format (jamais pour une panne réseau), bascule affichée ; choix Auto / Media3 / VLC dans le lecteur (bouton Moteur), **mémorisé par contenu** ; une seule session à la fois ; statistiques propres à chaque moteur (mesuré / estimé / déclaré / N/D, jamais recopiées) ; **journal des 20 dernières sessions** (démarrage, interruptions, images perdues, bascule, erreur) dans Réglages pour comparer les moteurs.
- **Phase 5 — Turbo** : réserve étendue (Media3 60–120 s, VLC 8 s de cache), **plafond mémoire 96 Mo**, synchronisation et enrichissement en pause pendant la lecture ; bouton Turbo dans le lecteur + réglage par défaut ; aucun gain promis : à mesurer avec le journal.
- **Phase 6** : taille du texte (normale / grande / très grande), animations réduites (réglage ADITV en plus du système), **minuterie de veille** (15 min à 2 h, fin du programme, fin du film ; avertissement 60 s avant, une touche prolonge), **export / import** des préférences, équipes, favoris et Ma liste (aucun secret), **version signée** préparée (clé personnelle hors dépôt, R8), **recette** dans `docs/RECETTE.md`.
- APK de test ≈ 65 Mo (LibVLC). 80 tests.

### 0.6.0 (20/11/2026) — demandes d'Adama : équipes, chaînes sport françaises, Top 10
- **Équipes suivies d'office** : FC Barcelone, Real Madrid, PSG, Manchester City, Aston Villa, **Sénégal** (+ autres équipes et compétitions proposées dans Réglages → Sport).
- **Matchs trouvés dans le guide uniquement** (titre, catégorie, chaîne de sport ; accents ignorés ; « Espanyol » ≠ Barça, « Atlético » ≠ Real ; un documentaire sur le Sénégal n'est pas un match) ; un match diffusé sur plusieurs chaînes = un seul rappel, sur la meilleure chaîne (favorite, française, meilleure version).
- **Rappels** : automatiques pour les équipes suivies (pas pour les rediffusions/magazines, jamais recréés après refus), « Me rappeler » sur tout programme à venir du guide ; délai 5/10/15/30 min ; **bandeau même pendant une lecture** (Menu ≡ pour regarder), **notification** si ADITV est fermée (l'ouvrir lance la chaîne), alarmes **reprogrammées au redémarrage**.
- **« Sport en direct »** : chaînes de sport françaises en première rangée sous l'affiche, zapping ↑/↓ entre elles ; filtre **Sport** dans le direct.
- **Top 10 films et séries du moment** : tendances TMDB de la semaine, limitées aux titres présents dans l'abonnement (meilleure version VF/qualité), rang affiché en grand chiffre.
- Base v5 (migration 4→5 testée). 75 tests.

### 0.5.0 (20/11/2026) — design validé par Adama appliqué à l'appli
Reprise fidèle du prototype `design/prototype/` (validé), sur les vraies données :
- **Charte** : anthracite profond, blanc, turquoise discret ; polices **Outfit** (titres) et **Inter** (texte) embarquées (OFL) ; icônes vectorielles ; focus évident (boutons blancs, liseré blanc + halo turquoise sur les cartes, léger agrandissement) ; cartes **cinémascope** alignées, affiches quand il n'y a pas de visuel TMDB (jamais d'image étirée).
- **Navigation** : barre haute compacte (Accueil, Direct, Films, Séries, Ma liste · heure · recherche · réglages), masquée au défilement ; ↑ y monte sur la rubrique active, ↓ revient sur l'élément quitté ; Retour : contenu → barre → Accueil → « Quitter ADITV ? ». L'ancien menu latéral est retiré.
- **Accueil** immersif (surtitre, grand titre, infos + badges VF/qualité, Reprendre / Plus d'infos / Ma liste, barre de reprise, pages ; ←/→ en bout de boutons change de page ; défilement automatique désactivé si animations réduites), rangées Reprendre la lecture, Votre soirée commence ici, En direct maintenant, Ma liste, Séries à suivre.
- **Fiches** film/série/chaîne : grand titre, infos, résumé, boutons, versions annoncées (panneau « Versions »), saisons en puces + épisodes (vu, durée, progression), distribution, « Vous aimerez aussi », « À propos ».
- **Films / Séries** : puces de catégories, tri (fournisseur, A → Z, récents), grille alignée, squelettes de chargement, états vides et import en cours.
- **Direct** = guide TV : aperçu de la chaîne/du programme, filtres, grille horaire restylée, repère « maintenant », panneau programme.
- **Lecteur** : titre, barre de lecture focalisable (←/→ = 10 s), boutons ronds, « Audio et sous-titres » (un seul panneau), Version, **Statistiques réelles** lues dans Media3 (image, codec, HDR, débit déclaré, bande passante estimée, réserve, images perdues, interruptions, audio), chacune marquée mesuré / estimé / déclaré / N/D ; bandeau de zapping.
- **Recherche** : clavier à l'écran (lettres, chiffres), résultats Films / Séries / Chaînes, état « aucun résultat ».
- **Ma liste** : filtres Tout / Films / Séries / Chaînes favorites, état vide avec raccourcis.
- **Ouverture animée** (2,4 s, logo + reflet + son), au démarrage à froid seulement, n'attend jamais le réseau, toute touche la passe ; image et son désactivables (Réglages → Ouverture) ; animations réduites : version courte.
- Non affichés car pas encore livrés (aucun faux bouton) : **Turbo** (phase 5) et **rappels de matchs** (phase 3 bis).
- 69 tests (parcours télécommande réécrit pour la nouvelle barre, en API 25 et 35 ; ouverture ; fiche série). Captures : `design/captures-app/`.

### 0.4.1 (20/11/2026) — retour d'Adama : « seules les chaînes sont synchronisées, jamais films et séries »
- **Cause identifiée** : import séquentiel chaînes → films → séries ; sur un gros bouquet, l'import des chaînes (long sur la Stick 1re gén.) était interrompu (appli quittée / veille) et reprenait par les chaînes au démarrage suivant → films/séries jamais atteints (« jamais synchronisé », aucune erreur).
- **Deux synchronisations indépendantes, en parallèle** : chaînes d'un côté, films + séries de l'autre ; un verrou par type ; un type déjà en cours n'est pas relancé.
- Réglages → Catalogue local : boutons « Synchroniser les chaînes » et « Synchroniser films et séries », avancement par type, « import commencé mais non terminé » si interrompu (plus jamais silencieux).
- Au démarrage : chaque groupe jamais abouti ou ancien est relancé immédiatement (sans attendre 12 h).
- Écran Films/Séries vide : affiche l'import en cours et le nombre de titres reçus.
- **Performance** : la détection VF/qualité recompilait ~20 expressions régulières par titre ; précompilées (import des gros bouquets nettement plus rapide).
- 65 tests (dont films importés pendant un import de chaînes bloqué, import interrompu signalé).

### 0.4.0 (20/11/2026) — retours d'Adama : « films toujours vides », « interface comme Apple TV »
- **Films/séries vides (2e cause probable)** : beaucoup de gros serveurs refusent la liste complète des films/séries (réponse vide ou trop lente) mais la servent par catégorie. Import en repli **catégorie par catégorie** (les titres sont rattachés à la catégorie interrogée) ; une catégorie en échec ne masque rien. Réglages → Catalogue local affiche désormais, par type : éléments, catégories, **mode d'import** et anomalies. Resynchronisation unique au démarrage. Testé sur serveur simulé — **à confirmer sur l'abonnement d'Adama**.
- **Interface inspirée d'Apple TV, identité ADITV** (pas de marque ni logo Apple) :
  - menu latéral **flottant en verre fumé**, masqué par défaut : ← au bord gauche ou Retour sur un écran principal l'ouvre ; logo + heure en tête ; retient la rubrique ; → ou Retour le referme et rend le focus ; le focus n'en sort pas par ↑/↓ ;
  - accueil : **visuel plein écran en carrousel** (défilement auto, indicateur de pages), logo-titre TMDB ou titre, ligne d'infos, pilule « Lire / Reprendre » + boutons ronds (Ma liste, Fiche, Suivant) libellés au focus ; rangées « Continuer à regarder », chaînes favorites, Ma liste, nouveaux films/séries en **vignettes paysage** avec élévation au focus ;
  - **fiche plein écran** : fond TMDB, logo-titre, pilule + boutons ronds (Ma liste, bande-annonce, correspondance ; favori et guide pour une chaîne), versions, épisodes en vignettes, suggestions, informations ;
  - logos-titres et fonds TMDB récupérés (`images`), fiches déjà en cache rechargées en arrière-plan ;
  - « Paramètres » renommé « Réglages ».
  - Sans clé TMDB, les visuels restent ceux du fournisseur (affiches) sur fonds colorés : **la clé TMDB est fortement recommandée** pour l'effet Apple TV.
- Base v4 (diagnostic d'import, logos) : migration 1→4 testée. **63 tests**, parcours télécommande complet réécrit pour le nouveau menu (API 25 et 35).

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
- [x] 0.3.1 testée : films toujours vides.
- [x] 0.4.0 : seules les chaînes synchronisées.
- [x] 0.5.0 : films et séries présents, clé TMDB ajoutée.
- [ ] Installer `ADITV-0.8.7-debug.apk` et dérouler `docs/RECETTE.md` (dont la saisie par QR code).
- [ ] Juger le design sur le téléviseur : lisibilité des textes à distance, fluidité du focus sur la Stick 1re génération.
- [ ] Ajouter une clé TMDB (Réglages → Métadonnées TMDB) pour les grands visuels et logos-titres.
- [ ] Lire un film, un épisode, une chaîne ; zapper ↑/↓ ; ouvrir le Guide TV et noter la couverture affichée et le temps d'actualisation.
- [ ] Optionnel : créer une clé TMDB et la saisir dans Paramètres → Métadonnées TMDB.

## Demandé par Adama
- [ ] Logo ADITV qui ne s'affichait plus : visible dans les captures 0.5.0+ ; à confirmer sur la télé.
- [x] **Rappels de matchs à venir** (0.6.0) — détail ci-dessous, à valider sur la Stick :
  - « 🔔 Me rappeler » sur un programme à venir du guide ; à l'heure (avance réglable, 5 min par défaut) : bandeau dans l'appli avec « Regarder » (même pendant une lecture), sinon notification Fire TV (comportement à vérifier sur Fire OS 6).
  - Rangée « Matchs à venir » (accueil) + filtre Sport dans le guide : catégorie du guide (Sport, Football…) et titres « A – B », compétitions ; favoris d'abord ; meilleure version (VF, qualité max).
  - Suivre une équipe / compétition : rappels automatiques sur les programmes correspondants, confirmation si le nom est ambigu.
  - Liste des rappels, suppression, survie au redémarrage (alarmes reprogrammées), nettoyage des rappels passés.
  - Limites : dépend de la qualité du guide fournisseur ; horaires du guide ≠ coup d'envoi exact.

### 0.20.0 (25/11/2026) — multiplex, thème OLED, notifications
- **Multiplex** (lecteur → Plus → « Multiplex : 2 chaînes à l'écran » → 2e chaîne de la liste de zapping) : 2 lecteurs Media3, partage 50/50 ; ←/→ écran actif (liseré turquoise, le son suit) ; OK = focus (actif 76 %, l'autre en vignette) ↔ partage ; Retour = chaîne active en plein écran. Session principale libérée avant ; 2e flux ouvert 1,2 s après ; HTTP 458 → « abonnement : pas 2 chaînes en même temps ». 2 connexions requises.
- **Thèmes** (Réglages → Démarrage, thème et veille) : Cinéma (charte), **Nuit OLED** (noir pur, surfaces très sombres, aucun halo), **Automatique** (OLED 20 h – 7 h). Palette `ThemeKind` commutable (état Compose), fenêtre et verre suivent.
- **Notifications redessinées** (`AditvNotification`) : carte de verre glissant depuis la droite, liseré + pastille colorés (turquoise rappel, corail direct/but, pulsation), petites capitales, touche « ≡ » dessinée, barre de temps restant. Rappels et buts.
- **Rappels** : plus de rappel automatique pour un autre sport du même club (basket, Euroligue, rugby…, chaînes Motorsport) — cas « Real Madrid – Zalgiris Kaunas » sur Motorsport 02.

### 0.19.0 (25/11/2026) — suggestions, Mes applis, écran de veille
- **« Parce que vous avez regardé « X » »** (accueil) : 2 derniers titres regardés > 5 min par le profil → recommandations TMDB (1 requête/titre, 12 h) limitées à l'abonnement (titres déjà reconnus), ordre TMDB, 15 max.
- **« Mes applis »** (accueil, après Reprendre) : applis TV installées (Netflix, Prime Video, YouTube, Disney+, myCANAL… d'abord), bannière TV, ouverture d'un clic ; écrans système exclus.
- **Écran de veille cinéma** : après 3 min sans télécommande (Réglages → Démarrage : jamais / 3 / 5 / 10 min), hors lecture et seulement ADI TV au premier plan : grands visuels du catalogue en fondu avec zoom lent, titre, heure et date ; toute touche le ferme ; écran gardé allumé 60 min au plus.

### 0.18.1 (25/11/2026) — mise à jour vraiment proposée
- Constat d'Adama : aucune proposition. Cause : vérification seulement au démarrage à froid du processus, or la Fire TV garde ADI TV en mémoire (retour depuis l'accueil Fire TV = simple reprise). Désormais : vérification à chaque retour au premier plan (au plus toutes les 30 min), jamais pendant une lecture ; « Plus tard » = 12 h pour cette version ; échec de vérification noté dans le journal de plantage.

### 0.18.0 (25/11/2026) — partage simplifié
- **Guide d'installation** public : `docs/INSTALLER_ADI_TV.md` (Downloader, adresses « dernière version » `releases/latest/download/ADITV-stick|cube.apk`, premier lancement, clés gratuites, mises à jour, plusieurs TV, dépannage). Codes courts is.gd / aftv.news à créer par Adama (réseau de l'environnement de dev bloqué vers ces sites).
- **Assistant de premier démarrage** (nouvel utilisateur, une fois ; jamais si TMDB + API-Football déjà là) : 1) ses équipes (sélection vide au départ, pas celles d'Adama), 2) options gratuites TMDB / API-Football / football-data.org saisies par QR code ; tout facultatif.
- **Copier vers une autre TV de la maison** : Réglages → saisir l'adresse + code affichés par l'autre TV (écran de connexion → « Recevoir d'une autre TV ») ; abonnement + 3 clés copiés ; réseau local seulement, code 4 chiffres + jeton, usage unique (`ConfigTransfer`, `RemoteEntryServer.Form.TRANSFER`).
- Première version publiée par GitHub Actions.

### Clé de signature et publication (choix A d'Adama, 25/11/2026)
- Les versions installées sont signées avec LA clé d'ADI TV (certificat SHA-256 `8ff3e1ce…9b694e`). Une mise à jour signée autrement est refusée par Android → désinstallation, perte des réglages.
- **Publication = GitHub Actions** (`.github/workflows/release.yml`) : à chaque changement du numéro de version sur la branche, GitHub construit Cube + Stick, signe avec le secret `ADITV_KEYSTORE_B64`, vérifie l'empreinte (`scripts/publish_update.py`), crée la release `vX.Y.Z` (APK + latest.json) et met à jour `updates/latest.json`.
- **Sessions suivantes** : ne JAMAIS livrer un APK construit localement sans vérifier l'empreinte (`publish_update.py` le fait et refuse sinon) ; livrer de préférence les APK de la release GitHub. Notes de version : `updates/NOTES.txt`.

### 0.17.0 (25/11/2026) — profils, dernière chaîne, alertes FootAdi, mise à jour automatique, bandes-annonces dans ADI TV
- **Pause au lancement** : confirmée corrigée par Adama (0.16.4).
- **Bandes-annonces dans ADI TV** : écran Android dédié (`TrailerActivity`, WebView seul plein écran, sans Compose autour) — OK pause/lecture, Menu ≡ = appli YouTube, Retour = fiche ; fin ou erreur → appli YouTube.
- **Profils** (`Profiles`) : chacun sa liste, sa progression (« Reprendre ») et ses chaînes favorites (clé « compte~profil » ; le profil principal garde les données d'avant). « Qui regarde ? » au démarrage s'il y en a plusieurs ; initiale du profil à côté de l'heure ; Réglages → Profils (ajouter, renommer, supprimer, utiliser).
- **Démarrage sur la dernière chaîne** (Réglages → Démarrage) : dernière chaîne du profil, une fois par lancement ; Retour → accueil.
- **Alertes FootAdi** : « Vos équipes aujourd'hui » sur l'accueil (OK → FootAdi) ; « ⚽ BUT ! » en haut de l'écran pendant les matchs des équipes suivies, même en lecture (1 requête / 5 min au plus pendant le match, rien en mode économie).
- **Mise à jour automatique** : `updates/latest.json` + APK sur le dépôt GitHub public (`scripts/publish_update.py`) ; vérifiée au lancement (et Réglages → Application) ; téléchargement avec progression, contrôle taille + SHA-256, puis installeur Android. Autorisation « Installer des applis inconnues » demandée une fois. ⚠️ Les mises à jour exigent la même clé de signature : voir « Clé de signature » ci-dessous.

### 0.16.6 (25/11/2026) — bandes-annonces dans l'appli YouTube, Top 10 dans Films / Séries
- **Bandes-annonces** : toujours le son sans l'image avec le lecteur YouTube intégré (WebView) sur la Fire TV d'Adama, malgré la 0.16.4. Désormais ouvertes dans l'**appli YouTube officielle** (Fire TV, puis YouTube Android TV, puis tout lecteur de liens YouTube) ; Retour revient dans ADI TV. Lecteur intégré seulement si aucune appli YouTube. Bandes-annonces de fond (Cube) désactivées.
- **Top 10 du moment en tête de l'onglet Séries** (et des films dans l'onglet Films), sélectionné à l'ouverture ; même calage de rangée que l'accueil (affiches entières).

### 0.16.5 (24/11/2026) — accueil : rangée choisie calée en haut, affiches entières
- La rangée précédente restait à moitié visible (seulement ses légendes : espace perdu) et la rangée choisie, calée en bas, avait ses affiches coupées. Désormais (défilement « pivot » vertical) : l'affiche choisie se place juste sous le titre de sa rangée, en haut de la zone ; la rangée précédente disparaît entièrement ; glissade douce. Dans les rangées, l'élément choisi glisse vers la gauche (même règle partout).
- Zone des rangées remontée (256 dp) : marge sous les légendes, même avec une TV qui rogne les bords.

### 0.16.4 (24/11/2026) — pause au lancement, bandes-annonces sans image, fond premium, Canal+ Sport, bas d'écran
- **Pause au lancement** : la Fire TV arrête brièvement l'appli (changement de fréquence de l'écran, écran système) ; au retour, la vidéo était rouverte en pause. Désormais : arrêt bref (< 5 s) → la lecture continue ; libération seulement après 5 s ; direct rouvert en lecture. Plus de prise du focus audio (une bande-annonce ne peut plus mettre la lecture en pause). Garde de démarrage : 4 premières secondes, toute pause non demandée est annulée.
- **Bandes-annonces sans image (son seul)** : sur Fire TV le WebView affiche la vidéo derrière la fenêtre ; fenêtre, fonds et visuel derrière la bande-annonce deviennent transparents pendant qu'elle est à l'écran (`VideoHole`), WebView transparent et accéléré, plus de transparence appliquée au WebView (voile en fondu par-dessus).
- **Fond premium** : noir bleuté + halo bleu nuit lumineux en haut à droite, reflet en bas à gauche, pointe de turquoise, vignettage.
- **Direct : famille « Canal+ Sport »** (après Ligue des champions) : Canal+ Sport, Foot, Sport 360, Premier League, Canal+ Live, versions outre-mer ; métropole d'abord.
- **Bas d'écran** : Top 10 ramené à la hauteur disponible ; marge de sécurité en bas (Direct, FootAdi, Classements).

### 0.16.3 (24/11/2026) — rappel confirmé, points du carrousel, icône pleine, grand Top 10
- FootAdi : « Me rappeler le match » → bouton **« ✓ Rappel programmé »** (cloche pleine) + confirmation en bas de l'écran ; nouvel appui = rappel supprimé. État relu à l'ouverture du match.
- Accueil : **points** sous la vedette (pilule claire pour l'élément affiché, transition douce) au lieu de « 01 / 06 ».
- **Icône adaptative** (Android 8+, Cube) : fond sombre + logo dans la zone sûre ; le système ne réduit plus l'ancienne icône sur un fond gris.
- **Top 10** : grandes affiches (3 à 4 visibles), grand chiffre en relief qui s'éclaire au focus, affiche qui se soulève, défilement « pivot » animé (glissade douce, l'élément choisi reste à gauche) jusqu'au n° 10.

### 0.16.2 (24/11/2026) — classements via football-data.org
- Constat d'Adama : l'offre gratuite d'API-Football ne donne pas les classements de la saison en cours.
- **Deuxième source gratuite : football-data.org** (clé personnelle, offre gratuite à vie, 10 requêtes/minute) pour Ligue 1, Premier League, LaLiga, Serie A, Bundesliga et Ligue des champions. Utilisée en priorité dès que sa clé est enregistrée ; sinon API-Football, avec un message qui explique la solution.
- Réglages → FootAdi : bloc « Classements » (clé football-data.org, chiffrée, testée à l'enregistrement, saisissable par QR code). Ligue 2, Europa et Conférence : non couvertes par l'offre gratuite de football-data.org.
- Zones (LDC, Europa, Conférence, relégation) déduites du rang, football-data.org ne les fournissant pas ; gardé 6 h sur disque.

### 0.16.1 (23/11/2026) — charte couleurs « rendu premium » (docs/ADI_TV_CHARTE_COULEURS.md)
- Palette : fond noir bleuté #07111A (dégradé très léger depuis #0B1520), surfaces #101C28 / #162433 / #1B2B3B, texte #F4F7FA / #C3CED8 / #7F93A6, turquoise #22D3C5 (focus, sélection, progression), cyan doux #7EE7F2 (halos), corail #FF6B57 (direct), vert #26C281, or #E0B75F (rare).
- Blanc cassé au lieu du blanc pur pour les boutons principaux et les éléments sélectionnés ; halos de focus plus diffus et discrets ; verre plus mat (arête fine, presque sans reflet).
- Pastille « EN DIRECT » : fond corail, texte blanc, compacte, sans clignotement.
- Classements : forme et zones aux couleurs de la charte (vert, corail, turquoise, cyan doux) au lieu de couleurs concurrentes.

### 0.16.0 (23/11/2026) — FootAdi : classements
- Puce **🏆 Classements** après « Mes équipes » : Ligue 1, Premier League, LaLiga, Serie A, Bundesliga, Ligue 2, Ligue des champions, Ligue Europa, Conférence. Tableau : rang, écusson, équipe, J G N P, différence, points, forme (V/N/D en couleur) ; repère de zone à gauche (turquoise LDC, orange Europa, vert Conférence, rouge relégation) ; équipes suivies surlignées ; poules séparées si la compétition en a.
- Bouton **Classement** dans le panneau d'un match (ouvre la compétition et la saison du match).
- Coût : **1 requête par compétition consultée**, gardée 6 h (disque) ; mode économie : on garde l'ancien classement.

### 0.15.4 (23/11/2026) — menu comme avant + espace, FootAdi rapide, pas de pause au lancement, HTTP 458
- **Menu** : retour au nom **sous** l'icône (choix d'Adama) ; contenu des écrans descendu à 84 dp (FootAdi, Direct, Recherche, Ma liste, Réglages, vedette Accueil/Films/Séries) pour que le nom ne recouvre plus rien.
- **FootAdi : chaînes affichées beaucoup plus vite** — un seul passage pour tous les matchs (chaînes, correspondances, guide lus une fois ; titres du guide normalisés une fois ; seulement les programmes des chaînes de l'abonnement) au lieu de tout refaire pour chaque match ; expressions précompilées.
- **Plus de pause au lancement d'une chaîne** : l'appui OK qui lance la chaîne (souvent maintenu, donc répété par la télécommande) atteignait le bouton Lecture/Pause ; OK ignoré pendant la 1re seconde du lecteur, répétitions d'un OK maintenu toujours ignorées.
- **HTTP 458 / 429 / 509 / 503** (serveur qui limite les connexions) : message clair, puis **jusqu'à 3 nouveaux essais automatiques** (2 s, 4 s, 6 s) en libérant d'abord la connexion précédente ; « Serveur occupé : nouvel essai (n/3)… » à l'écran.

### 0.15.3 (23/11/2026) — nom de l'icône dans la pastille
- Barre : au focus, la pastille blanche s'élargit et contient l'icône + le nom (le nom sous l'icône recouvrait la puce « Demain » de FootAdi).

### 0.15.2 (23/11/2026) — compositions sur terrain, noms de chaînes entiers
- **Compositions façon terrain** (demande d'Adama, thème ADI TV) : terrain horizontal vert sombre (bandes, lignes discrètes, halo turquoise), domicile à gauche / extérieur à droite, joueurs placés d'après la grille de l'API (sinon la formation), **photos** (serveur d'images d'API-Football, hors quota), anneau aux couleurs du maillot, formation en pastille turquoise, entraîneur et remplaçants en bas.
- Liste FootAdi : **noms de chaînes en entier** (colonne 150 dp, 2 lignes si besoin).
- Correction lint (FocusRequester mémorisé par ligne de match) ; la 0.15.1 publiée sans APK.

### 0.15.1 (23/11/2026) — FootAdi : chaînes en lignes, compositions ; menu aéré
- Panneau du match : **une chaîne par ligne**, pleine largeur ; noms d'équipes lisibles ; panneau défilant.
- **Compositions** : arrivent dans la **même requête groupée** que les buteurs (`/fixtures?ids=`, 20 matchs) — aucune requête dédiée ; demandées à partir de 70 min avant le coup d'envoi (au plus toutes les 10 min tant qu'elles ne sont pas publiées), puis gardées sur disque. Panneau : formations + « Voir les compositions » (fenêtre plein écran : titulaires numérotés, postes, entraîneur, remplaçants).
- Barre d'icônes plus aérée (16 dp entre les icônes).

### 0.15.0 (23/11/2026) — barre d'icônes façon Fire TV, « Liquid Glass »
- **Barre du haut en icônes** (photo d'Adama) : Recherche · Accueil · Films · Séries · En direct · FootAdi · Ma liste · Réglages dans une capsule de verre ; au focus, carré blanc arrondi + nom dessous ; rubrique active : verre plus clair + point turquoise. Logo à gauche, heure à droite.
- **Liquid Glass** (`LiquidGlass.kt`) : voile translucide, reflet en haut, arête lumineuse dégradée, lueur basse — sans flou temps réel (indisponible avant Android 12, trop coûteux sur Fire TV). Appliqué à la barre, aux boutons secondaires, aux puces, aux lignes et au panneau FootAdi, aux fenêtres (quitter, rappels, guide, lecteur) et aux panneaux latéraux.

### 0.14.3 (23/11/2026) — FootAdi économe (clé gratuite : 100 requêtes/jour)
- Cache **sur disque** (survit au redémarrage) : une date = 1 requête ; jour passé terminé = jamais redemandé ; direct 5 min, aujourd'hui 30 min, à venir 6 h ; ménage après 10 jours.
- **Buteurs groupés** : 1 requête pour 20 matchs commencés du jour (au lieu d'une par match), seulement si l'on reste 1,2 s sur un match ; buts des matchs terminés gardés sur disque.
- Rafraîchissement auto (5 min, seulement s'il y a du direct) **uniquement quand l'écran FootAdi est affiché** — plus pendant qu'on regarde la chaîne.
- **Quota affiché** en bas de la liste (« restantes aujourd'hui : N / 100 », d'après l'API) ; sous 10 : mode économie (plus de rafraîchissement auto ni de buteurs, on garde ce qui est en cache).

### 0.14.2 (23/11/2026) — retours d'Adama : Sénégal, chevauchements
- **Famille Sénégal = la catégorie « Sénégal » du fournisseur** (ou préfixe SN), plus de devinette par le nom (une « RTS » suisse n'y va plus) ; la catégorie Sénégal l'emporte sur le préfixe FR.
- **Lecteur** : commandes de lecture à gauche, options à droite sur une même ligne, sans superposition (les boutons agrandis passaient sur ↑ / lecture / ↓).
- **Vedette (Accueil, Films, Séries)** : hauteur bornée — titre 34 sp, résumé réduit à 1 ligne quand le titre tient sur 2 lignes ; ne recouvre plus « Tout parcourir ». Test de mise en page avec un titre et un résumé longs.

### 0.14.1 (23/11/2026) — Direct : chargement plus rapide et jamais bloqué
- Classement des chaînes ≈ 2,5× plus rapide (expressions précompilées, pays déduit une fois par catégorie, pas de normalisation Unicode pour les noms ASCII, drapeau Ligue des champions calculé une seule fois).
- Une erreur de chargement n'affiche plus des cases grises sans fin : message « Chaînes indisponibles (cause) ».
- Test de performance : 20 000 chaînes.

### 0.14.0 (23/11/2026) — FootAdi (matchs façon FotMob → chaîne qui diffuse)
- **Nouvel onglet FootAdi** après En direct : jours (avant-hier → J+3) et **★ Mes équipes** ; matchs groupés par compétition (Ligue des champions, Ligue 1, Premier League, Liga, Serie A, Bundesliga, Europa League, Conference League, Ligue 2, CAN, Sénégal, équipes de France et du Sénégal) ; écussons, score en direct (minute), heure de coup d'envoi.
- **Panneau du match** : compétition · journée, score/compte à rebours, buteurs, **« Où regarder dans votre abonnement »** : chaînes trouvées dans le guide TV (titre citant les deux équipes, −45 min / +30 min autour du coup d'envoi), françaises d'abord, sinon le diffuseur habituel en France (Canal+, beIN SPORTS, Ligue 1+/DAZN). **OK lance directement la chaîne** ; « Me rappeler le match ».
- **Données** : API-Football (clé gratuite personnelle, chiffrée, saisie dans Réglages → FootAdi ou par QR code depuis le téléphone, testée à l'enregistrement) ; cache 5 min en direct, 30 min le jour même ; scores rafraîchis toutes les 5 min tant qu'un match est en cours. Mode démo : matchs fictifs.
- Limite : l'offre gratuite d'API-Football peut restreindre certaines saisons/compétitions ; le message de l'API est alors affiché tel quel.

### 0.13.0 (23/11/2026) — chaînes reclassées, Ligue des champions, bandes-annonces intégrées, son plus grave, icône
- **Direct par familles** (maquette validée par Adama) : ★ Favoris, **Ligue des champions**, Sport France, Cinéma France, Divertissement France, Info France, Jeunesse France, Documentaires France, **Sénégal**, Afrique, puis Autres pays (francophones d'abord). Chaînes **groupées par diffuseur** (beIN SPORTS 1, 2, 3…), français d'abord. Classement automatique (préfixe pays, noms connus, catégorie fournisseur), calculé en arrière-plan ; **Menu ≡ → Déplacer vers…** pour corriger (mémorisé). Zapping ↑/↓ dans la même famille et le même ordre.
- **Ligue des champions** : matchs du guide (8 jours, en cours compris) sur la meilleure chaîne (française d'abord) — OK pour regarder, ou pour un rappel s'il est à venir ; puis les diffuseurs 2026-2027 : **Canal+** (France ; finale aussi sur M6), **Canal+ Afrique** et **New World TV** (Afrique francophone).
- **Logos** : repli sur les logos du guide TV quand le fournisseur n'en donne pas ; nom de chaîne en grand sinon.
- **Bandes-annonces TMDB dans ADI TV** (lecteur YouTube officiel) : plein écran depuis la fiche (OK pause, Retour), et **fond muet en boucle sur le Cube** après 3 s sur un titre (accueil, Films/Séries, fiche). Vidéo non intégrable : appli YouTube.
- **Son de navigation** plus grave et plus rond (≈ 500 Hz + corps grave ; validation ≈ 300 Hz).
- **Icône** : carré plein et opaque (plus de coins transparents → plus de bandes grises dans la tuile Fire TV) ; bannière sur le même fond.

### 0.12.0 (22/11/2026) — nouvelle direction « cinéma » demandée par Adama (interface jugée trop « IPTV classique »)
Choix d'Adama : directement dans l'appli ; aperçu vidéo réel du direct ; textes « Grand ».
- **Textes ≈ +30 %** partout (échelle de base 1,3 en plus du réglage d'accessibilité).
- **Accueil cinéma** : fond plein écran qui **suit l'élément sélectionné** (fondu enchaîné), grand titre / logo-titre, infos et résumé TMDB fixes en haut ; **une rangée à la fois** dans la moitié basse ; cartes plus grandes ; sous-titres gris des rangées supprimés.
- **Films / Séries** : même vedette plein écran ; **une rangée par catégorie** (titres chargés à l'apparition de la rangée), « Ajouts récents » en tête ; **Tout parcourir** ouvre la grille complète (filtres, tri).
- **En direct** : **aperçu vidéo réel, muet, plein écran de la chaîne sélectionnée sur le Cube** (720p max, 0,7 s après l'arrêt de la sélection, une connexion de l'abonnement ; arrêté en quittant l'écran ou en arrière-plan, jamais à la place de la lecture lancée) ; Stick : logo en grand. Liste des chaînes sur voile sombre, **logos en grand sur plaques**, programme en grand en bas à droite.
- **Chaînes** partout : plaque sobre, logo en grand, nom en grand sans logo (plus de dégradé générique).
- **Navigation** : soulignement turquoise animé de la rubrique active, agrandissement plus marqué des cartes au focus.

### 0.11.0 (22/11/2026) — interface retravaillée d'après les 4 maquettes d'Adama + son de navigation
- **Barre du haut** : logo à gauche, Accueil · Films · Séries · En direct (rubrique active soulignée), à droite Recherche, Ma liste (signet), Réglages.
- **Boutons** : rectangles arrondis (blanc plein / contour blanc), liseré turquoise au focus — partout.
- **Accueil** : « À l'affiche », Regarder + Ma liste + infos, qualité « selon la source » sous les boutons, compteur 01 / 06, rangée « Reprendre » en 16:9 avec titre et temps restant.
- **En direct** (nouvel écran) : chaînes à gauche (logo, programme en cours, progression, favori), aperçu à droite (programme, horaires, progression, à suivre, description), Regarder / Guide TV / favori. Filtre partagé avec le guide, rafraîchi chaque minute, calculs hors interface.
- **Guide TV** (ouvert depuis En direct) : en-tête « Guide TV », jour et ville du fuseau, −6 h / +6 h / Jour suivant / Maintenant, rappel des touches OK · Retour · ◀ ▶.
- **Lecteur** : logo, retour, titre, badges qualité/langue, « Informations de lecture » ; en bas temps · barre · durée, « En cours de lecture », −10 / pause / +10 (↑/↓ en direct), Audio, Sous-titres, Turbo, Infos, Plus (moteur, version, veille, favori).
- **Son de navigation** : petit « tic » doux synthétisé (original), seulement quand la sélection bouge à la télécommande ; son de validation sur OK ; Réglages → Ouverture → « Son de navigation ».
- Limites : l'aperçu du Direct montre le logo de la chaîne (le guide ne fournit pas d'image de programme) ; lecteur non capturable en démo (pas de flux).

### 0.10.1 (22/11/2026) — Adama : navigation un peu lente, souhaite la sensation Apple TV / Fire TV
- Cause principale : APK de test (debug) — Compose non optimisé, 2 à 3× moins fluide. Nouvelle version **« rapide »** (R8, non débogable, profils de démarrage Compose installés par ProfileInstaller) **signée et identifiée comme la version de test** : s'installe par-dessus sans perdre compte ni réglages. APK Cube ≈ 27 Mo, Stick ≈ 25 Mo.
- Défilement « pivot » (élément sélectionné à position fixe) : déjà actif par défaut sur Fire TV (Compose). Transitions entre écrans raccourcies (120/80 ms), assombrissement de l'accueil 250 ms.

### 0.10.0 (22/11/2026) — nouveau logo officiel (pack « ADI_TV_Logo_Pack_Claude ») et nom « ADI TV »
- Logo : tracés du SVG du pack convertis tels quels en dessins vectoriels Android (nets en 1080p et 4K, quelques Ko), en calques superposables : ADI, play rouge, TV, trois traits. En-tête ≈ 160 px de large en 1080p (charte : 135–190 px).
- Icône : emblème carré du pack (A + play + traits) ; bannière : logo complet sur noir #050505.
- Ouverture : scénario du pack (souffle, ADI se dévoile avec le play, TV, trois traits discrets, stabilisation, fondu, sans étincelles) étiré sur le son validé (4,39 s), fin synchronisée avec le son ; animations réduites : logo fixe.
- Nom affiché : « ADI TV » (appli, textes, page téléphone). Identifiant technique inchangé.

### 0.9.5 (22/11/2026) — demandes d'Adama sur le Cube + icône trop petite
- Cube : grandes images de fond TMDB en **pleine qualité** (original, ramenées à la taille de l'écran au décodage) ; **guide TV mis à jour tout de suite** (sans attendre 90 s d'inactivité) ; **mises à jour de fond permises pendant la lecture** (catalogue, guide, TMDB, Turbo compris). Stick inchangée.
- Icône et bannière : logo agrandi au maximum sans recadrage (bannière : 94 % de la largeur ; icône carrée : toute la largeur) ; bannière déclarée aussi sur l'activité principale (lanceur Fire TV).

### 0.9.4 (22/11/2026) — Cube identifié par adb : Fire OS 7 (API 28), abilist armeabi-v7a,armeabi
- Version Cube : ARMv7 seulement (arm64 retiré, inutile) → APK ≈ 58 Mo.

### 0.9.3 (22/11/2026) — installation refusée sur le Cube : INSTALL_FAILED_NO_MATCHING_ABIS
- Le Cube 3e génération a un processeur 64 bits mais un **Fire OS 32 bits** : la version Cube embarque désormais LibVLC ARMv7 **et** arm64 (Fire OS choisit). Bibliothèques natives compressées : APK Cube ≈ 103 Mo, APK Stick 65 → 46 Mo.

### 0.9.2 (22/11/2026) — bon son d'ouverture (fourni par Adama, MP3 4,39 s)
- Remplace le son de la 0.9.1 (mauvais fichier). Intégré tel quel (volume déjà correct, fondu naturel) ; animation calée sur 4,39 s, impact ≈ 1,25 s inchangé.

### 0.9.1 (22/11/2026) — nouveau logo ADI TV et nouveau son d'ouverture (fournis par Adama)
- Logo (kit 8K transparent) : icône, bannière Fire TV (320×180 et 480×270, sans fond ajouté), écran de connexion, barre du haut, chargement.
- Ouverture : animation calée sur le son (4,36 s) — montée (halo doré), impact du logo à ≈ 1,25 s (pose + éclat), reflet, légère avancée, extinction **terminée en même temps que le son**, puis le menu. Démarre au même instant que le son ; une touche passe l'ouverture et coupe le son. Son : volume remonté (+6 dB), fondu final de 0,6 s pour une fin nette.

### 0.9.0 (22/11/2026) — début de la version Fire TV Cube 3e génération (TV Samsung QE55Q65R)
- **Deux versions** : `stick` (ARMv7, API 25) et `cube` (ARMv7 + arm64, API 28+). Profil d'appareil (`DeviceProfile`) : réglages étendus sur le Cube, garde-fous conservés partout.
- **Fréquence de l'écran adaptée au flux** (les deux versions, réglage dans Lecture, activé par défaut) : cadence déclarée ou mesurée sur les premières images (Media3) / déclarée par VLC ; 25/50 i/s → 50 Hz, 24 → 24 Hz, 23,976 → 23,976 Hz ; retour au mode d'origine en quittant le lecteur.
- TV d'Adama : Samsung QE55Q65R (QLED 4K 2019) : HDR10, HDR10+, HLG ; pas de Dolby Vision. Conseils : Signal d'entrée plus, Anynet+.
- Cube : Fire OS 7 (Android 9, API 28), système 32 bits. Reste à recevoir : nombre de connexions (multiplex), barre de son éventuelle.

## Prochaine étape : version Fire TV Cube (3e génération), arrivée dimanche
Adama passe au Fire TV Cube. À recevoir : version de Fire OS (Paramètres → Ma Fire TV → À propos), nombre de connexions de l'abonnement (multiplex), modèle de TV et matériel son.
Plan proposé : 1) APK arm64 (Stick conservée en secours) ; 2) multiplex 2 flux (écran partagé ou incrustation) ; 3) décodage matériel élargi, HDR, Dolby en sortie ; 4) réglages moins bridés (images, Turbo, tâches de fond), garde-fous conservés ; 5) accueil instantané (dernier accueil affiché puis mis à jour) ; 6) conseil Ethernet. Migration : export des préférences sur la Stick, saisie des accès par QR code sur le Cube.

## Étape précédente
Recette sur la Stick (`docs/RECETTE.md`), puis corrections selon les retours d'Adama. Version finale signée quand Adama aura créé sa clé (`docs/BUILD_ET_INSTALLATION.md`).

## 0.21.0
- Multiplex « réalisateur » : score en direct sur chaque panneau (API-Football, cache), pastille « BUT ! » et bascule automatique du son/focus sur le match où il y a but (sauf choix manuel < 3 min). Touche Menu = activer/désactiver.
- Pause automatique « Toujours là ? » : après N h sans télécommande (2/3/4 h/Jamais, défaut 3 h, Paramètres > Démarrage, thème et veille), pause + question ; sans réponse en 2 min, arrêt de la lecture (connexion libérée).

## 0.21.1
- Fiche : portraits TMDB des acteurs (format distribution « Nom|Rôle|photo », anciennes fiches rechargées), distribution et « À propos » focalisables (on peut descendre), retour en haut quand le focus revient sur l’en-tête.

## 0.21.2
- Alertes de but : relevé léger `fixtures?live=all` fusionné dans la liste du jour, partagé alertes + réalisateur ; rythme = temps de jeu restant / (quota − réserve), borné 1–5 min (`pollInterval`, test `LivePollTest`).
