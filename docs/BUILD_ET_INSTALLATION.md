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
L'APK debug s'identifie comme `com.aditv.app.debug`, version `0.1.0-debug` : il peut cohabiter avec un futur build release.

## Installer sur la Fire TV Stick (une seule fois : activer le débogage)
1. Fire TV : **Paramètres → Mon Fire TV → À propos**, sélectionner 7 fois le nom de l'appareil pour afficher les *Options pour les développeurs* (inutile sur les anciens Fire OS où le menu est déjà visible).
2. **Paramètres → Mon Fire TV → Options pour les développeurs** : activer **Débogage ADB**.
3. Noter l'adresse IP : **Paramètres → Mon Fire TV → À propos → Réseau**.
4. Depuis l'ordinateur, sur le même réseau :
   ```bash
   adb connect <IP-de-la-stick>:5555     # accepter l'autorisation affichée sur la TV
   adb install -r ADITV-0.1.0-debug.apk
   ```
5. ADITV apparaît dans **Vos applis et chaînes** (bannière ADITV). Après usage, le débogage ADB peut être désactivé.

Mise à jour : `adb install -r` sur un APK signé avec la **même clé** conserve les données (compte, catalogue). La clé debug est propre à chaque machine de développement : si la clé change, désinstaller d'abord (`adb uninstall com.aditv.app.debug`) — compte et catalogue local sont alors effacés.

## À relever sur la Stick (pour la phase 2)
- Modèle et version Fire OS (**À propos**).
- Si la connexion au fournisseur échoue : le message affiché à l'écran (il ne contient jamais vos accès).
