# LibVLC : classes appelées depuis le code natif (JNI) et par réflexion.
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# Récepteurs déclarés dans le manifeste (rappels, redémarrage) : conservés par AAPT, rien à ajouter.
# kotlinx.serialization, Room, Media3, OkHttp fournissent leurs propres règles.
