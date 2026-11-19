# ADITV — synthèse, architecture et plan MVP (phase 0)

Source de référence : `docs/dossier/` (dossier v1.0 du 19/11/2026). Ce document ne répète pas les spécifications : il les tranche et les ordonne.

## 1. Décisions retenues
| Sujet | Décision |
|---|---|
| Cible | App personnelle, Fire TV Stick 4K, pilotage 100 % télécommande. Aucune publication. |
| Accès | URL + utilisateur + mot de passe. Adaptateur Xtream **à vérifier** sur le vrai fournisseur ; M3U après MVP. |
| Pile | Kotlin, Compose for TV, Room, DataStore, WorkManager, Media3 (ExoPlayer), LibVLC. Aucun Google Play Services. Un seul module `app` au départ. |
| Données | Catalogue fournisseur = vérité de disponibilité. TMDB = habillage seulement (jamais qualité/pistes). EPG = sources réelles, jamais de programme inventé. |
| Lecture | Contrat `PlaybackEngine` commun ; Media3 par défaut provisoire ; Auto = 1 bascule bornée sur erreur de compatibilité identifiée ; 1 seule session active. |
| Mesures | Chaque métrique porte un état `mesuré / estimé / déclaré / N/D`. Pas de recopie d'une métrique d'un moteur à l'autre. |
| Turbo | Politique de stabilité (réserve + plafond mémoire + suspension des tâches de fond), pas une promesse de débit. Pas de multiconnexion. |
| Secrets | Mot de passe chiffré (clé Android Keystore), exclu des backups et des logs ; clé TMDB saisie localement par l'utilisateur. |
| Identité | Anthracite `#090D12`, surfaces `#111922`/`#18232E`, texte `#F5F7FA`/`#ADB8C5`, accent `#00D7D7`. Logo validé ; tracé vectoriel à reconstruire pour l'app. |
| Intro | 2,4 s au démarrage à froid uniquement, image et son désactivables séparément, n'attend jamais le réseau. |

## 2. Inconnues — seules celles qui bloquent
| Inconnue | Bloque | Comment la lever |
|---|---|---|
| ~~SDK Android non téléchargeable dans l'environnement cloud~~ | — | ✅ `dl.google.com` autorisé. |
| ~~Génération exacte de la Stick + version Fire OS~~ | — | ✅ Fire TV Stick 4K (1re génération), Fire OS 6.7.1.1 (Android 7.1, API 25). |
| API du fournisseur réellement compatible Xtream ? | Adaptateur réel (phase 1) | Test de connexion sur ta Stick avec tes accès, saisis **dans l'app**, jamais dans le chat. |
| Clé/jeton TMDB | Enrichissement (phase 2) | Compte TMDB gratuit personnel, saisi dans Paramètres. |

Non bloquant (à mesurer plus tard) : codecs/HDR/audio réels, sources EPG, limite de sessions, modes HDMI.

## 3. Architecture
```
ui/            Compose TV : écrans, composants focus, thème ADITV
  home/ detail/ live/ epg/ search/ player/ settings/ login/
domain/        modèles + cas d'usage légers (matching, reprise, politique de bascule)
data/
  provider/    ProviderRepository  ← XtreamAdapter | DemoAdapter (identifié « Démo »)
  catalog/     CatalogRepository   (Room, import incrémental par lots + marque de fraîcheur)
  metadata/    MetadataRepository  (TMDB, file priorisée sur les éléments visibles, backoff 429/5xx)
  epg/         EpgRepository       (XMLTV streaming, DTD désactivée, UTC, fenêtre -1/+7 j)
  prefs/       PreferencesRepository (DataStore) + SecretStore (Keystore)
playback/      PlaybackController (1 session), PlaybackEngine {Media3Engine, VlcEngine},
               PlaybackMetrics par moteur, TurboPolicy
work/          WorkManager : sync catalogue, EPG, enrichissement (suspendus en lecture difficile)
```
Flux : UI → ViewModel (StateFlow) → cas d'usage → repositories → API / Room. I/O et parsing sur `Dispatchers.IO`.

Entités Room : `ProviderAccount`, `CatalogItem`, `Category`, `Channel`, `SeriesSeason`, `Episode`, `MetadataMatch`, `EpgSource`, `ChannelEpgMapping`, `Programme`, `FavoriteChannel`, `WatchlistEntry`, `WatchProgress`, `PlaybackSessionAgg` — clés toujours préfixées par `accountId` (détail : `dossier/03_Technique/01_ARCHITECTURE_ET_DONNEES.md`).

## 4. Parcours D-pad
- **Menu latéral** compact (icônes) ; il se déplie quand il reçoit le focus et **retient sa sélection** : y revenir par ← ramène sur la rubrique active, pas sur l'icône la plus proche.
- **Accueil** : focus initial sur *Reprendre* ; ↓ parcourt les rangées ; ← depuis la 1re colonne ouvre le menu ; chaque rangée retient sa colonne.
- **Retour** : ferme le panneau ouvert → écran précédent avec item/rangée restaurés → à la racine, confirmation de sortie.
- **Lecteur** : OK affiche les commandes ; ←/→ = seek ±10 s **seulement en VOD** et barre active ; en direct ↑/↓ = chaîne suivante/précédente ; Diagnostic et Turbo sont des boutons visibles (jamais uniquement un appui long).
- **Fin d'épisode** : suggestion du suivant annulable (préférence autoplay).

## 5. Plan MVP (séquence, pas calendrier)
| Phase | Contenu | Sortie |
|---|---|---|
| 0 ✅ | Dossier versionné, synthèse, 3 maquettes HF | Direction visuelle validée |
| 1 ✅ code | Projet Gradle compilable, thème, navigation, connexion (test + erreurs), catalogue Room incrémental, adaptateur Démo + Xtream | APK debug installable ; connexion réelle testée sur la Stick |
| 2 ✅ code | TMDB + matching + correction manuelle, fiches, Media3, progression/reprise | Un film et un épisode réellement lus et repris |
| 3 ✅ code | Direct, favoris, EPG (XMLTV/API), grille virtualisée | Couverture EPG mesurée |
| 3 bis | Rappels de matchs à venir (guide, équipes suivies, bandeau / notification) | Rappel reçu et bascule sur la chaîne sur la Stick |
| 4 | LibVLC, politique Auto, métriques par moteur | Comparaison reproductible, 1 seule session |
| 5 | Turbo, intro + son, optimisation | Gain mesuré… ou absence de gain documentée |
| 6 | PIN adultes, accessibilité, sommeil, export prefs, recette | APK + sources + limites |

## 6. Maquettes
`design/maquettes/index.html` (1920×1080, navigable au clavier comme une télécommande) et captures dans `design/maquettes/captures/`.
Contenus 100 % fictifs ; la photo du hero vient de la maquette de référence fournie. Les visuels « affiche » sont des dégradés génératifs à remplacer par les images TMDB.
