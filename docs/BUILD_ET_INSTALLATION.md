# Compiler et installer ADITV

## Prérequis (machine de développement)
- JDK 17 ou plus récent (testé avec OpenJDK 21).
- SDK Android : `platforms;android-37.2`, `build-tools;37.0.0`, `platform-tools`. Android Studio les installe ; en ligne de commande : `sdkmanager "platforms;android-37.2" "build-tools;37.0.0" "platform-tools"`.
- Créer `local.properties` (non versionné) : `sdk.dir=/chemin/vers/android-sdk`.
- Aucun secret n'est nécessaire pour compiler.

## Compiler
```bash
./gradlew assembleStickDebug     # Fire TV Stick 4K 1re gén. : app/build/outputs/apk/stick/debug/app-stick-debug.apk
./gradlew assembleCubeDebug      # Fire TV Cube 3e gén. (64 bits, Android 9+) : app/build/outputs/apk/cube/debug/app-cube-debug.apk
./gradlew testStickDebugUnitTest # tests unitaires + parcours UI Robolectric (API 25 et 35 ; captures dans design/captures-app/)
./gradlew lintStickDebug lintCubeDebug
sha256sum app/build/outputs/apk/*/debug/*.apk
```
L'APK debug s'identifie comme `com.aditv.app.debug`, version `0.4.1-debug` : il peut cohabiter avec un futur build release.

## Deux versions
- **stick** : Fire TV Stick 4K 1re génération (Fire OS 6, 32 bits) ; réglages prudents.
- **cube** : Fire TV Cube 3e génération (Fire OS 7+ ; système 32 bits sur processeur 64 bits : les deux jeux de bibliothèques sont embarqués) ; réglages étendus (Turbo 180 s / 200 Mo, cache d'images 25 %, grilles par 480, « Toutes » 600 chaînes, démarrage des tâches de fond à 4 s). Même identifiant d'appli : installer la bonne version sur chaque appareil.

Sur la TV Samsung : Paramètres → Général → Gestionnaire de périphériques externes → **Signal d'entrée plus** activé pour la prise HDMI de la Fire TV (4K HDR à 60 Hz), et **Anynet+ (HDMI-CEC)**.

## Installer sur la Fire TV Stick (une seule fois : activer le débogage)
1. Sur la Stick 4K 1re génération (Fire OS 6), le menu **Options pour les développeurs** est déjà visible dans **Paramètres → Mon Fire TV**. (Sur les Fire OS plus récents : **À propos**, sélectionner 7 fois le nom de l'appareil.)
2. **Paramètres → Mon Fire TV → Options pour les développeurs** : activer **Débogage ADB**.
3. Noter l'adresse IP **de la Stick** : **Paramètres → Mon Fire TV → À propos → Réseau** (ce n'est pas l'adresse de la box, souvent 192.168.1.1).
   Sous Windows, `adb` vient des *SDK Platform Tools* de Google : décompresser l'archive, puis lancer les commandes depuis ce dossier (`.\adb.exe ...` dans PowerShell).
4. Depuis l'ordinateur, sur le même réseau :
   ```bash
   adb connect 192.168.1.42:5555     # remplacer 192.168.1.42 par l'IP de la Stick, sans < > ; accepter l'autorisation sur la TV
   adb install -r ADITV-0.4.1-debug.apk
   ```
5. ADITV apparaît dans **Vos applis et chaînes** (bannière ADITV). Après usage, le débogage ADB peut être désactivé.

Mise à jour : `adb install -r` sur un APK signé avec la **même clé** conserve les données (compte, catalogue). La clé debug est propre à chaque machine de développement : si la clé change, désinstaller d'abord (`adb uninstall com.aditv.app.debug`) — compte et catalogue local sont alors effacés.

## Clé TMDB (facultative)
1. Créer un compte gratuit sur themoviedb.org, puis **Paramètres du compte → API** : demander une clé (usage personnel).
2. Dans ADITV : **Réglages → Métadonnées TMDB**, saisir la *clé API* (v3) ou le *jeton d'accès en lecture* (v4), puis **Enregistrer et tester**.
Plus simple : **Depuis mon téléphone** affiche un QR code ; scanner avec le téléphone (même Wi-Fi que la Stick), coller la clé dans la page qui s'ouvre, Envoyer, puis **Enregistrer et tester** sur la TV. Même principe sur l'écran de connexion pour l'adresse, l'identifiant et le mot de passe du fournisseur.
La clé est chiffrée sur la Stick. Sans clé, les fiches affichent uniquement les informations du fournisseur.

## Guide TV
Le guide du fournisseur est importé automatiquement (au démarrage puis toutes les 12 h environ, jamais pendant une lecture). **Réglages → Guide TV** : couverture, actualisation manuelle, décalage horaire par source, ajout d'une source XMLTV en `https://`. Pour une chaîne mal associée : fiche de la chaîne → **Guide**.

## Bandes-annonces
Le bouton « Bande-annonce » n'apparaît que si une clé TMDB est configurée, que TMDB référence une vidéo, et que l'application **YouTube** est installée sur la Fire TV (Amazon Appstore).

## À relever sur la Stick
- Espace libre (**Stockage**) : l'appareil cible n'a qu'environ 1 Go disponible.
- Si la connexion au fournisseur échoue : le message affiché à l'écran (il ne contient jamais vos accès).

## Version signée (version finale, facultatif)
La version de test (`*-debug.apk`, identifiant `com.aditv.app.debug`) suffit pour l'usage personnel. Pour une version finale optimisée (R8) :
1. Sur ton PC, crée **ta** clé une seule fois (garde le fichier et les mots de passe hors du dépôt, ne les envoie jamais dans le chat) :
   `keytool -genkeypair -v -keystore aditv.jks -alias aditv -keyalg RSA -keysize 4096 -validity 10000`
2. À la racine du projet, crée `keystore.properties` (ignoré par git) :
   ```
   storeFile=aditv.jks
   storePassword=…
   keyAlias=aditv
   keyPassword=…
   ```
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

La version finale s'installe **à côté** de la version de test (identifiant différent) : comptes et préférences ne sont pas partagés. Utilise **Réglages → Accessibilité et sauvegarde → Exporter** puis **Importer** pour reprendre préférences, équipes suivies, favoris et Ma liste (jamais les accès ni la clé TMDB). Fichier : `Android/data/com.aditv.app[.debug]/files/aditv-preferences.json` (`adb pull` pour le copier).

Garde toujours la même clé : une mise à jour signée avec une autre clé est refusée par la Fire TV.

## Taille de l'APK
Depuis la 0.7.0, le second moteur VLC (LibVLC, ARMv7) ajoute environ 40 Mo : l'APK de test fait environ 65 Mo, la version finale environ 45 Mo.
