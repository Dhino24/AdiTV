# Versions retenues et licences (phase 1)
Versions figées dans `gradle/libs.versions.toml`. Aucune dépendance Google Play Services.

| Composant | Version | Licence |
|---|---|---|
| Gradle (wrapper) | 9.9.1 | Apache 2.0 |
| Android Gradle Plugin | 9.5.1 | Apache 2.0 |
| Kotlin, plugins Compose et sérialisation | 2.4.21 | Apache 2.0 |
| KSP | 2.3.14 | Apache 2.0 |
| Compose BOM / Compose for TV (tv-material) | 2026.11.01 / 1.1.0 | Apache 2.0 |
| AndroidX activity, lifecycle, navigation, core | 1.13.0 / 2.11.1 / 2.10.2 / 1.19.1 | Apache 2.0 |
| Room / DataStore / WorkManager | 2.8.5 / 1.3.0 / 2.12.0 | Apache 2.0 |
| kotlinx.coroutines / kotlinx.serialization | 1.11.0 / 1.13.0 | Apache 2.0 |
| OkHttp | 5.5.1 | Apache 2.0 |
| Media3 (ExoPlayer, HLS, OkHttp, UI) | 1.11.1 | Apache 2.0 |
| Coil 3 | 3.6.3 | Apache 2.0 |
| Désucrage des API Java (java.time sur Android 7.1) | desugar_jdk_libs 2.1.5 | GPL 2 + exception de liaison (Classpath) |
| Tests : JUnit 4, MockWebServer, Robolectric, room-testing | 4.13.2 / 5.5.1 / 4.18.1 / 2.8.5 | EPL 1.0 / Apache 2.0 / MIT / Apache 2.0 |

SDK : `compileSdk` 37.2, `targetSdk` 34, `minSdk` 25 = Fire OS 6.7.1.1 de la Stick 4K 1re génération (confirmé). ABI embarquée : `armeabi-v7a` uniquement. Le lint bloque tout appel d'API postérieur à Android 7.1.

Données TMDB : usage personnel non commercial avec attribution (« This product uses the TMDB API but is not endorsed or certified by TMDB. »), affichée dans les fiches et les paramètres. Clé personnelle de l'utilisateur, jamais embarquée.

Prévu plus tard : LibVLC (LGPL 2.1+, phase 4 — obligations de la LGPL à respecter si l'APK est un jour distribué).
