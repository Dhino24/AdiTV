# Compiler et installer ADITV

## Prérequis (machine de développement)
- JDK 17 ou plus récent (testé avec OpenJDK 21).
- SDK Android : `platforms;android-37.2`, `build-tools;37.0.0`, `platform-tools`. Android Studio les installe ; en ligne de commande : `sdkmanager "platforms;android-37.2" "build-tools;37.0.0" "platform-tools"`.
- Créer `local.properties` (non versionné) : `sdk.dir=/chemin/vers/android-sdk`.
- Aucun secret n'est nécessaire pour compiler.

## Compiler
```bash
./gradlew assembleDebug          # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests unitaires + parcours UI Robolectric (captures dans design/captures-app/)
sha256sum app/build/outputs/apk/debug/app-debug.apk
```
L'APK debug s'identifie comme `com.aditv.app.debug`, version `0.4.0-debug` : il peut cohabiter avec un futur build release.

## Installer sur la Fire TV Stick (une seule fois : activer le débogage)
1. Sur la Stick 4K 1re génération (Fire OS 6), le menu **Options pour les développeurs** est déjà visible dans **Paramètres → Mon Fire TV**. (Sur les Fire OS plus récents : **À propos**, sélectionner 7 fois le nom de l'appareil.)
2. **Paramètres → Mon Fire TV → Options pour les développeurs** : activer **Débogage ADB**.
3. Noter l'adresse IP **de la Stick** : **Paramètres → Mon Fire TV → À propos → Réseau** (ce n'est pas l'adresse de la box, souvent 192.168.1.1).
   Sous Windows, `adb` vient des *SDK Platform Tools* de Google : décompresser l'archive, puis lancer les commandes depuis ce dossier (`.\adb.exe ...` dans PowerShell).
4. Depuis l'ordinateur, sur le même réseau :
   ```bash
   adb connect 192.168.1.42:5555     # remplacer 192.168.1.42 par l'IP de la Stick, sans < > ; accepter l'autorisation sur la TV
   adb install -r ADITV-0.4.0-debug.apk
   ```
5. ADITV apparaît dans **Vos applis et chaînes** (bannière ADITV). Après usage, le débogage ADB peut être désactivé.

Mise à jour : `adb install -r` sur un APK signé avec la **même clé** conserve les données (compte, catalogue). La clé debug est propre à chaque machine de développement : si la clé change, désinstaller d'abord (`adb uninstall com.aditv.app.debug`) — compte et catalogue local sont alors effacés.

## Clé TMDB (facultative)
1. Créer un compte gratuit sur themoviedb.org, puis **Paramètres du compte → API** : demander une clé (usage personnel).
2. Dans ADITV : **Réglages → Métadonnées TMDB**, saisir la *clé API* (v3) ou le *jeton d'accès en lecture* (v4), puis **Enregistrer et tester**.
La clé est chiffrée sur la Stick. Sans clé, les fiches affichent uniquement les informations du fournisseur.

## Guide TV
Le guide du fournisseur est importé automatiquement (au démarrage puis toutes les 12 h environ, jamais pendant une lecture). **Réglages → Guide TV** : couverture, actualisation manuelle, décalage horaire par source, ajout d'une source XMLTV en `https://`. Pour une chaîne mal associée : fiche de la chaîne → **Guide**.

## Bandes-annonces
Le bouton « Bande-annonce » n'apparaît que si une clé TMDB est configurée, que TMDB référence une vidéo, et que l'application **YouTube** est installée sur la Fire TV (Amazon Appstore).

## À relever sur la Stick
- Espace libre (**Stockage**) : l'appareil cible n'a qu'environ 1 Go disponible.
- Si la connexion au fournisseur échoue : le message affiché à l'écran (il ne contient jamais vos accès).
