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
| Coil 3 | 3.6.3 | Apache 2.0 |
| Tests : JUnit 4, MockWebServer, Robolectric | 4.13.2 / 5.5.1 / 4.18.1 | EPL 1.0 / Apache 2.0 / MIT |

SDK : `compileSdk` 37.2, `targetSdk` 34, `minSdk` 25 (Fire OS 6 = plus ancienne Stick 4K sous Android). À ajuster une fois le modèle connu.

Prévu plus tard : Media3 (Apache 2.0, phase 2) ; LibVLC (LGPL 2.1+, phase 4 — obligations de la LGPL à respecter si l'APK est un jour distribué).
