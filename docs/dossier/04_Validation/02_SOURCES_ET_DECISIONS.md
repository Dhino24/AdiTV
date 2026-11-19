# Sources et décisions techniques
Sources primaires consultées le 19 novembre 2026. Les pages évoluent ; vérifier les versions lors de l’implémentation. Aucune version de dépendance non contrôlée n’est imposée dans ce dossier.

- Amazon — identifier les appareils : https://developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html
- Amazon — comparaison matérielle : https://developer.amazon.com/docs/device-specs/device-specifications-comparison-table.html
- Amazon — modes HDMI : https://developer.amazon.com/docs/fire-tv/4k-apis-for-hdmi-mode-switch.html
- Amazon — recommandations UX : https://developer.amazon.com/docs/fire-tv/design-and-user-experience-guidelines.html
- Android — Media3 : https://developer.android.com/media/media3
- Android — analytics : https://developer.android.com/media/media3/exoplayer/analytics
- Android — piles réseau : https://developer.android.com/media/media3/exoplayer/network-stacks
- Android — LoadControl : https://developer.android.com/reference/androidx/media3/exoplayer/LoadControl
- VideoLAN — source Android / LibVLC : https://github.com/videolan/vlc-android
- TMDB — démarrage : https://developer.themoviedb.org/docs/getting-started
- TMDB — authentification : https://developer.themoviedb.org/docs/authentication-application
- TMDB — FAQ/licences/attribution : https://developer.themoviedb.org/docs/faq
- TMDB — images : https://developer.themoviedb.org/docs/image-basics
- TMDB — regroupement des réponses : https://developer.themoviedb.org/docs/append-to-response

## Décisions déduites pour ADITV
Double moteur proposé pour comparer compatibilité et qualité de service, pas pour affirmer que VLC est toujours supérieur. Turbo conçu comme politique de stabilité, pas comme augmentation garantie du débit. Contrat de mesures par moteur car les métriques diffèrent. Catalogue fournisseur et métadonnées séparés. EPG total visé mais couverture externe mesurée. Résolution vidéo 4K distincte du rendu UI. Ces décisions sont des choix de conception fondés sur les capacités documentées ; elles restent à tester sur l’appareil.
