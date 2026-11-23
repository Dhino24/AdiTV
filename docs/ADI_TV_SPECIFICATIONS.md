# ADI TV — Spécifications de l'application

Version décrite : **0.13.0** (23 novembre 2026) — voir aussi `PROJECT_STATE.md` pour les évolutions 0.12 (direction cinéma) et 0.13 (familles de chaînes, Ligue des champions, bandes-annonces intégrées)
Projet : application IPTV personnelle pour Amazon Fire TV, développée pour Adama.

> ADI TV **ne fournit aucun contenu**. Elle lit l'abonnement IPTV de l'utilisateur (protocole Xtream Codes) et l'enrichit : métadonnées TMDB, guide TV, sport.

---

## 1. Appareils et versions

| | Fire TV Stick 4K (1re génération) | Fire TV Cube (3e génération) |
|---|---|---|
| Système | Fire OS 6.7.1.1 = Android 7.1 (API 25) | Fire OS 7 = Android 9 (API 28) |
| Architecture utilisée | 32 bits ARMv7 | 32 bits ARMv7 (processeur 64 bits, système 32 bits) |
| Mémoire | 1,5 Go | 2 Go |
| Version d'ADI TV | `stick` (réglages prudents) | `cube` (réglages étendus) |

**Téléviseur d'Adama :** Samsung QE55Q65R (QLED 4K, 2019), compatible HDR10, HDR10+ et HLG, sans Dolby Vision.
- À activer côté TV : **Signal d'entrée plus** et **Anynet+ (HDMI-CEC)** sur la prise HDMI de la Fire TV.

### Les versions de l'APK

| Fichier | Usage |
|---|---|
| `…-cube-rapide.apk` | **Usage quotidien sur le Cube** : optimisée (R8, profils de démarrage Compose) |
| `…-stick-rapide.apk` | Usage quotidien sur la Stick |
| `…-cube-debug.apk` / `…-stick-debug.apk` | Diagnostic uniquement : 2 à 3 fois moins fluide |

- Les versions « rapide » et « debug » partagent le même identifiant (`com.aditv.app.debug`). On passe de l'une à l'autre par-dessus, sans perdre compte ni réglages.
- La version finale signée avec une clé personnelle (`com.aditv.app`) est prête côté projet. La clé reste hors du dépôt (voir `docs/BUILD_ET_INSTALLATION.md`).

### Différences Stick / Cube (`DeviceProfile`)

| Réglage | Stick | Cube |
|---|---|---|
| Turbo (avance de vidéo Media3) | 60–120 s, 96 Mo max | 60–180 s, 200 Mo max |
| Cache VLC en Turbo | 8 s | 12 s |
| Cache d'images en mémoire | 15 % | 25 % |
| Grilles : titres chargés par morceau | 240 | 480 |
| Direct « Toutes » : chaînes affichées | 200 | 600 |
| Tâches de fond au démarrage | après 12 s | après 4 s |
| Images de fond TMDB | 1280 px | pleine qualité |
| Guide TV | attend 90 s sans toucher la télécommande | mis à jour tout de suite |
| Mises à jour pendant la lecture | en pause | permises |

---

## 2. Identité visuelle

- **Logo officiel** (pack « ADI_TV_Logo_Pack_Claude ») : « ADI » blanc avec un triangle play rouge dans le A, capsule « TV », trois traits discrets vert / jaune / rouge (Sénégal).
  - Les tracés du SVG du pack sont convertis tels quels en dessins vectoriels Android, découpés en calques : ADI, play, TV, traits.
- **Icône** : l'emblème carré du pack (le A, le play et les traits).
- **Bannière Fire TV** : le logo complet sur fond noir `#050505`.
- **Nom affiché** : « ADI TV ».
- **Charte de l'interface** :
  - fond anthracite (`#07090D`), texte blanc (`#F5F7FA`), accent turquoise discret (`#00D7D7`) pour le focus, rouge `#FF4D5E` pour le direct ;
  - polices **Outfit** (titres) et **Inter** (texte), embarquées (licence OFL).
- **Focus télécommande** : léger agrandissement, liseré turquoise, halo. Les boutons sont des rectangles arrondis : blanc plein (principal) ou contour blanc (secondaire).

### Ouverture animée
- **Son** : fichier validé par Adama (4,39 s).
- **Animation calée sur le son**, qui démarre et se termine en même temps que lui :

| Moment | Ce qui se passe |
|---|---|
| 0 – 0,25 s | souffle lumineux |
| → 1,25 s (temps fort) | « ADI » se dévoile, le play se pose |
| ≈ 1,25 – 1,75 s | « TV » glisse en place |
| ≈ 1,6 – 2,1 s | les trois traits apparaissent |
| jusqu'à ≈ 3,8 s | stabilisation, léger reflet au sol |
| ≈ 3,8 – 4,39 s | fondu final, puis le menu |

- Uniquement au démarrage à froid. N'attend jamais le réseau. Une touche passe l'ouverture et coupe le son.
- Image et son désactivables séparément (Réglages → Ouverture). Avec les animations réduites : logo fixe.

### Son de navigation
- Petit « tic » doux et original (synthétisé pour le projet), joué **seulement quand la sélection se déplace à la télécommande**. Jamais au chargement d'un écran, ni en butée.
- Son de validation un peu plus rond sur OK.
- Cadence limitée quand une touche est maintenue.
- Désactivable : Réglages → Ouverture → Son de navigation.

---

## 3. Navigation

- **Barre du haut** :
  - à gauche : le logo, puis une **capsule de verre d'icônes** : Recherche · Accueil · Films · Séries · En direct · FootAdi · Ma liste · Réglages ;
  - icône sélectionnée : pastille blanche arrondie qui s'élargit avec le nom ; rubrique active : verre plus clair et point turquoise ;
  - à droite : l'heure et l'indicateur de mise à jour du catalogue.
- La barre se masque au défilement.
  - **↑** depuis le haut d'un écran monte sur la rubrique active.
  - **↓** revient exactement sur l'élément quitté.
- **Retour** : contenu → barre → Accueil → « Quitter ADI TV ? » (Rester / Quitter).
- **Défilement « pivot »** façon Apple TV (élément sélectionné à position stable). Transitions courtes : 120 ms en entrée, 80 ms en sortie.

---

## 4. Écrans

### Connexion
- Adresse du serveur, identifiant, mot de passe. Bouton « Afficher » qui révèle le mot de passe 5 s.
- **Tester la connexion** : statut, expiration, connexions simultanées, fuseau du serveur. Puis **Enregistrer et importer**.
- HTTP non chiffré accepté seulement après un avertissement explicite, et uniquement vers l'hôte saisi.
- **Saisie depuis le téléphone (QR code)** :
  - la Stick ou le Cube sert une petite page sur le Wi-Fi local ;
  - on y tape ou colle ses accès, qui arrivent dans les champs ;
  - sécurité : jeton de 128 bits dans le QR, code à 4 chiffres pour une adresse tapée à la main, 5 essais faux maximum, usage unique, fermeture après 10 minutes, aucune valeur journalisée.
- **Mode démo** : catalogue, chaînes et guide fictifs.

### Accueil
- **Grand visuel en carrousel** :
  - « À l'affiche » ou « Reprendre la lecture », titre ou logo-titre TMDB, infos et résumé ;
  - **Regarder**, **+ Ma liste**, ⓘ fiche ;
  - qualité annoncée « selon la source » ;
  - compteur « 01 / 06 » ; ← / → au bout des boutons pour changer de page.
- **Rangées** :
  - Sport en direct (chaînes sport françaises) ;
  - Matchs à venir ;
  - **Reprendre** (16:9, titre et temps restant) ;
  - Top 10 films, Top 10 séries ;
  - Votre soirée commence ici (ajouts récents) ;
  - En direct maintenant (favoris) ;
  - Ma liste ;
  - Séries à suivre.
- **Mise à jour des films et séries proposée toutes les 3 semaines** dans un bandeau : « Mettre à jour » ou « Plus tard » (repousse de 3 jours).

### Films / Séries
- Puces de catégories : « Tous les films / Toutes les séries » puis les catégories du fournisseur. Tri : ordre du fournisseur, A → Z, plus récents.
- **Grille chargée par morceaux** (jamais tout le catalogue en mémoire). Le total s'affiche sans tout charger.
- États vides, import en cours avec le nombre de titres reçus, squelettes de chargement.

### Fiche (film, série, chaîne)
- Fond et logo TMDB, infos, résumé, distribution, « Vous aimerez aussi », « À propos ».
- **Versions annoncées** par le fournisseur (VF, VOSTFR, 4K…) avec choix de la version.
- Séries : saisons en puces, épisodes (vu, durée, progression).
- Bande-annonce : ouverte dans YouTube si TMDB en référence une et que l'appli est installée.
- Correction manuelle de la correspondance TMDB. Chaîne : favori et association à une source du guide.

### En direct
- **À gauche**, la liste des chaînes : logo, nom, programme en cours, barre d'avancement, étoile de favori.
- **Filtres** : Favoris, Sport, Toutes, catégories du fournisseur.
  - Le dernier filtre choisi est mémorisé.
  - « Toutes » n'est jamais choisi à l'ouverture, et il est plafonné (200 chaînes sur la Stick, 600 sur le Cube).
- **À droite**, l'aperçu : chaîne | programme, horaires, progression, « À suivre », description. Boutons **Regarder**, **Guide TV**, **Ajouter aux favoris**.
  - L'aperçu montre le logo de la chaîne : le guide ne fournit pas d'image par programme.
- Programmes rafraîchis au début de chaque minute, calculés en arrière-plan.

### Guide TV
- En-tête : « Guide TV », jour, ville du fuseau ; −6 h / +6 h / Jour suivant / **Maintenant**.
- Aperçu du programme sélectionné ; filtres partagés avec En direct ; couverture du guide.
- **Grille** : une ligne par chaîne, axe horaire commun qui suit le focus, repère « maintenant ».
- **Panneau programme** : regarder, fiche de la chaîne, « Me rappeler » pour un programme à venir, replay si le fournisseur déclare une archive (expérimental).
- Rappel des touches : OK Détails · Retour Fermer · ◀ ▶ Parcourir.

### Lecteur
- **En haut** : logo, retour, titre (et chaîne, maintenant / ensuite en direct) ; badges qualité / langue ; heure.
- **Informations de lecture** (bouton Infos) : statistiques réelles du moteur, chacune marquée mesuré / estimé / déclaré / N/D.
- **En bas** :
  - temps · barre de lecture (←/→ = 10 s) · durée totale ;
  - « En cours de lecture » ;
  - au centre : **−10 / pause / +10**, ou ↑/↓ pour changer de chaîne en direct ;
  - à droite : **Audio**, **Sous-titres**, **Turbo**, **Infos**, **Plus** (moteur, version, minuterie de veille, favori).
- **Zapping ↑/↓** dans la liste d'origine (favoris, catégorie, sport, toutes), avec bandeau de chaîne.
- Reprise de lecture, épisode suivant proposé.
- Audio français préféré ; sous-titres français préférés pour une version VOSTFR.
- **Fréquence de l'écran adaptée au flux** :
  - 25/50 i/s → 50 Hz (foot européen) ; 24 → 24 Hz ; 23,976 → 23,976 Hz ;
  - retour au mode d'origine en quittant le lecteur ;
  - désactivable (Réglages → Lecture).
- **Minuterie de veille** : 15 min à 2 h, fin du programme, fin du film. Avertissement 60 s avant ; une touche prolonge.

### FootAdi (matchs de foot)
- Jours (avant-hier → J+3) et **★ Mes équipes** ; ↓ va au premier match, ↑ revient sur le jour choisi.
- Matchs groupés par compétition (Ligue des champions, grands championnats, Coupes d'Europe, Ligue 2, CAN, Sénégal, équipes de France et du Sénégal) : écussons, score et minute en direct, heure sinon, chaîne qui diffuse.
- Panneau : compétition · journée, score ou compte à rebours, buteurs, **Où regarder dans votre abonnement** (chaînes du guide TV dont un programme cite les deux équipes autour du coup d'envoi, françaises d'abord ; sinon diffuseur habituel en France). **OK** lance la chaîne. « Me rappeler le match ».
- Source : **API-Football** avec la clé gratuite de l'utilisateur (Réglages → FootAdi, ou QR code). Cache sur disque : 5 min en direct / 30 min le jour même / 6 h à venir / définitif pour un jour passé terminé ; buteurs groupés (1 requête pour 20 matchs) ; rafraîchissement auto seulement écran affiché ; quota restant affiché, mode économie sous 10 requêtes. Démo : matchs fictifs.

### Recherche
- Clavier à l'écran. Résultats Films / Séries / Chaînes, une carte par contenu (versions regroupées).

### Ma liste
- Filtres Tout / Films / Séries / Chaînes favorites. État vide avec raccourcis.

### Réglages (sections)

| Section | Contenu |
|---|---|
| Compte fournisseur | statut, expiration, connexions |
| Catalogue local | nombres d'éléments, mode d'import, erreurs ; synchroniser les chaînes / les films et séries |
| Lecture | langue préférée (VF / VOSTFR / VO), qualité maximale (4K / 1080p / 720p), flux adaptatifs (débit max / adapté au réseau), fréquence de l'écran |
| Moteur, Turbo et diagnostic | choix par défaut, Turbo par défaut, journal des 20 dernières sessions |
| FootAdi | clé API-Football (aussi par QR code), test, suppression |
| Sport | équipes et compétitions suivies, délai des rappels, liste des rappels |
| Guide TV | sources XMLTV, actualisation, couverture, décalages |
| Métadonnées TMDB | clé personnelle (aussi saisissable par QR code), cache |
| Accessibilité et sauvegarde | taille du texte, animations réduites, export / import des préférences (sans aucun secret) |
| Ouverture | image, son de l'ouverture, son de navigation |
| Plantages | rapports, à lire sur le téléphone par QR code |
| Application | version, appareil, confidentialité, licences |

---

## 5. Catalogue et données

- **Import Xtream** : catégories puis titres, lus en flux, y compris quand le fournisseur renvoie un objet indexé.
  - Repli catégorie par catégorie si la liste complète est refusée.
  - `category_ids` lu quand `category_id` manque.
- **Deux synchronisations indépendantes**, en parallèle :
  - **chaînes** : automatique toutes les ≈ 12 h (au démarrage si nécessaire) ;
  - **films et séries** : importés automatiquement **une seule fois**, puis mise à jour **proposée toutes les 3 semaines**.
- Les titres retirés par le fournisseur sont masqués, jamais supprimés : la progression et les favoris sont conservés.
- **Variantes regroupées** : les versions d'un même contenu (VF, VOSTFR, MULTI, SD/HD/FHD/4K, préfixes pays) forment une seule carte. La meilleure selon les préférences est affichée et lue.
- **Base locale Room v5** : catalogue, progression, Ma liste, épisodes, TMDB, favoris, guide, sport. Migrations testées de 1 à 5.

### TMDB
- Clé personnelle de l'utilisateur, chiffrée sur l'appareil, testée avant enregistrement.
- Correspondance automatique, puis correction manuelle possible. Fonds, logos-titres, résumés, distribution, bandes-annonces.
- **Top 10 du moment** : tendances TMDB de la semaine, limitées aux titres présents dans l'abonnement. Recherche par titre exact indexée ; résultat gardé en mémoire.
- Attribution TMDB affichée (usage personnel non commercial).

### Guide TV (EPG)
- XMLTV du fournisseur, plus des sources https ajoutées.
  - Lecture en flux, gzip, limite 400 Mo, entités externes refusées.
  - Horaires en UTC, changement d'heure géré, fenêtre de −1 à +7 jours.
- Une source par chaîne (fournisseur prioritaire), décalages par source et par chaîne, association manuelle.
- Remplacement uniquement après un import complet : une panne ne vide pas le guide.

### Sport
- **Équipes suivies d'office** : FC Barcelone, Real Madrid, PSG, Manchester City, Aston Villa, Sénégal. D'autres équipes et compétitions sont proposées.
- **Détection des matchs dans le guide** :
  - accents ignorés ; homonymes écartés (« Espanyol » n'est pas le Barça, « Atlético » n'est pas le Real) ;
  - un documentaire n'est pas un match ;
  - un match diffusé sur plusieurs chaînes = un seul rappel, sur la meilleure chaîne.
- **Rappels** :
  - automatiques pour les équipes suivies (pas pour les rediffusions), ou « Me rappeler » depuis le guide ;
  - délai de 5, 10, 15 ou 30 min ;
  - bandeau même pendant une lecture (touche Menu pour regarder), notification si l'appli est fermée ;
  - reprogrammés au redémarrage de l'appareil.
- **Sport en direct** : chaînes de sport françaises en tête de l'accueil, zapping entre elles.

---

## 6. Lecture

- **Deux moteurs** derrière un contrat commun, une seule session à la fois :
  - **Media3** (ExoPlayer, par défaut, le plus léger) ;
  - **LibVLC 3.7.7** (lit davantage de formats).
- **Auto** : Media3, puis VLC **une seule fois** si Media3 refuse le format (jamais pour une panne réseau).
- Choix Auto / Media3 / VLC mémorisé **par contenu**.
- **Turbo** : réserve étendue avec un plafond mémoire (voir la section 1). Aucun gain n'est promis : à comparer avec le journal des sessions.
- Statistiques propres à chaque moteur, jamais recopiées de l'un à l'autre.

---

## 7. Stabilité et performances

- **Calculs lourds hors de l'interface** (listes, guide, programmes en cours, Top 10). C'était la cause des fermetures sur l'accueil : Fire OS ferme une appli figée.
- Grilles chargées par morceaux ; accueil allégé ; rafraîchissements limités pendant les synchronisations ; doublons d'identifiant retirés des listes.
- Erreurs des tâches de fond notées, jamais fatales. Moteur VLC protégé après sa libération. Mémoire d'images rendue quand Fire OS la réclame.
- **Enregistreur de plantages** local, sans URL ni accès dans les rapports :
  - erreur Java avec pile d'appels ;
  - fermeture alors que l'appli était à l'écran (détectée au lancement suivant) ;
  - blocage de l'interface de plus de 5 s, avec la pile ;
  - journal système d'ADI TV ;
  - derniers écrans, lectures et tâches de fond.
  - Les rapports se lisent sur le téléphone par QR code.

---

## 8. Sécurité et confidentialité

- Accès fournisseur, clé TMDB et clé API-Football **chiffrés** (Android Keystore), exclus des sauvegardes et des journaux. Un `Redactor` masque les secrets partout.
- Aucune télémétrie, aucun envoi externe. Les rapports de plantage restent sur l'appareil.
- L'export des préférences ne contient **aucun secret**.
- HTTP en clair seulement après consentement explicite, et vers l'hôte saisi uniquement. Redirections vers un autre hôte refusées.
- Saisie par QR code : HTTP simple, mais limité au réseau local (c'est indiqué à l'écran).

---

## 9. Technique

| Domaine | Choix |
|---|---|
| Langage / interface | Kotlin 2.4, Jetpack Compose + Compose for TV (tv-material 1.1) |
| Build | AGP 9.5, deux saveurs `stick` / `cube`, trois types de build `debug` / `rapide` / `release` |
| Données | Room (v5), DataStore, WorkManager |
| Réseau / images | OkHttp 5, Coil 3 |
| Lecture | Media3 1.11, LibVLC 3.7.7 |
| QR code | ZXing core 3.5.3 |
| Tests | 95 tests JVM et Robolectric, dont les parcours complets à la télécommande en API 25 et 35 (captures dans `design/captures-app/`) |

Les licences sont détaillées dans `docs/VERSIONS_ET_LICENCES.md`.

### Arborescence utile
- `app/src/main/java/com/aditv/` : `core` (journal, rapports, profil d'appareil), `data` (fournisseur, base, TMDB, guide, sport, préférences), `net`, `playback` (moteurs), `ui` (écrans).
- `docs/` : construction et installation, recette, licences, dossier produit et design, ce document.
- `PROJECT_STATE.md` : historique détaillé de chaque version et des retours d'Adama.
- `design/captures-app/` : captures réelles des écrans.

---

## 10. Limites connues et pistes

- **Lecture réelle, replay et TMDB avec une vraie clé** : à confirmer sur l'abonnement d'Adama. Le replay est expérimental.
- **Aperçu vidéo en direct** dans l'écran En direct : faisable sur le Cube, mais il utilise une connexion de l'abonnement. Non activé sans accord.
- **Multiplex** (deux flux à l'écran) : en attente du nombre de connexions simultanées de l'abonnement.
- **Profils utilisateurs** : non prévus (pas d'avatar).
- Code PIN adulte : écarté à la demande d'Adama.
