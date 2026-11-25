# Installer ADI TV sur une Fire TV

ADI TV est une application pour regarder **votre propre abonnement IPTV** (Xtream) sur Fire TV : accueil façon cinéma, films, séries, direct, guide TV, FootAdi (matchs, scores, chaînes qui diffusent).
ADI TV **ne fournit aucune chaîne ni aucun film** : il vous faut un abonnement IPTV à vous.

---

## 1. Préparer la Fire TV (une seule fois)

1. **Paramètres → Ma Fire TV → À propos** : cliquez **7 fois** sur le nom de l'appareil → « Vous êtes maintenant développeur ».
2. Installez l'appli gratuite **Downloader** (rechercher « Downloader » dans l'Appstore, icône orange).
3. **Paramètres → Ma Fire TV → Options pour les développeurs → Installer des applis inconnues** → **Downloader : Activé**.

## 2. Installer ADI TV

Dans **Downloader**, tapez l'adresse (ou le **code court**, voir plus bas) puis **Go** :

| Votre Fire TV | Adresse à taper |
|---|---|
| **Toutes les Fire TV** (Stick, Stick 4K, Lite…) | `github.com/Dhino24/AdiTV/releases/latest/download/ADITV-stick.apk` |
| **Fire TV Cube** (aperçu vidéo du direct en plus) | `github.com/Dhino24/AdiTV/releases/latest/download/ADITV-cube.apk` |

Le téléchargement démarre, puis **Installer** → **Ouvrir**. Vous pouvez ensuite supprimer le fichier téléchargé (Downloader le propose).

> Ces adresses donnent **toujours la dernière version**.

## 3. Premier lancement

1. **Connectez votre abonnement** : adresse du serveur, identifiant, mot de passe. Le plus simple : **« Saisir depuis mon téléphone (QR code) »** — scannez, collez, envoyez (le téléphone doit être sur le même Wi-Fi).
2. L'**assistant** vous propose ensuite :
   - **vos équipes** (leurs matchs sur l'accueil, rappels, alertes de but) ;
   - les **options gratuites** ci-dessous, chacune saisie depuis le téléphone par QR code. Tout est facultatif (« Passer »), et modifiable plus tard dans **Réglages**.

## 4. Options gratuites (facultatives, 2 minutes chacune)

| Option | Ce que ça apporte | Où créer la clé |
|---|---|---|
| **TMDB** | Affiches, résumés, bandes-annonces, Top 10 | [themoviedb.org](https://www.themoviedb.org/signup) → Paramètres → API → « Clé d'API » |
| **API-Football** | FootAdi : matchs, scores, chaînes, compositions (100 requêtes/jour) | [api-football.com](https://dashboard.api-football.com/register) → Dashboard → « API Key » |
| **football-data.org** | Classements (L1, PL, LaLiga, Serie A, Bundesliga, LDC) | [football-data.org](https://www.football-data.org/client/register) → clé reçue par e-mail |

Chacun crée **ses propres clés** (elles sont personnelles et gratuites).

## 5. Mises à jour automatiques

Quand une nouvelle version sort, ADI TV la propose au lancement : **« Mettre à jour »** puis **« Installer »**.
La première fois, la Fire TV demande d'autoriser ADI TV : **Paramètres → Ma Fire TV → Options pour les développeurs → Installer des applis inconnues → ADI TV : Activé** (ADI TV vous y emmène).

## 6. Plusieurs TV à la maison ?

Sur la nouvelle TV, à l'écran de connexion : **« Recevoir d'une autre TV »** — elle affiche une adresse et un code.
Sur la TV déjà configurée : **Réglages → Copier vers une autre TV de la maison** → saisissez l'adresse et le code → **Envoyer**.
L'abonnement et les clés sont copiés (même Wi-Fi). À réserver à **vos** TV : ce sont vos accès.

## 7. En cas de souci

| Problème | Solution |
|---|---|
| « Application non installée » | Désinstallez une ancienne version d'ADI TV d'une autre source, puis réinstallez. |
| Downloader ne trouve pas l'adresse | Vérifiez l'orthographe (majuscules comprises : `Dhino24/AdiTV`). |
| Le bas de l'écran est coupé | TV Samsung : Image → Taille de l'image → **Adapter à l'écran**. Fire TV : Affichage → **Calibrer l'affichage**. |
| « Serveur occupé (HTTP 458) » | Votre abonnement limite le nombre d'écrans : fermez-le sur les autres appareils. ADI TV réessaie seul. |

---

### Pour Adama : créer les codes courts (une seule fois, 2 minutes)

Les adresses GitHub sont longues à taper à la télécommande. Créez des liens courts :
1. Sur un ordinateur ou un téléphone, ouvrez **is.gd**.
2. Collez l'adresse longue de la version **Stick** (tableau du §2, précédée de `https://`), dépliez « Customise », tapez l'alias **`aditvstick`**, validez → `is.gd/aditvstick`.
3. Même chose pour le **Cube** avec l'alias **`aditvcube`**.
4. Pour un **code chiffré Downloader** (encore plus court) : sur **aftv.news**, créez un code pour l'adresse longue ; Downloader l'accepte directement (ex. `12345`).

Les liens courts restent valables à chaque nouvelle version (ils visent « la dernière version »).
