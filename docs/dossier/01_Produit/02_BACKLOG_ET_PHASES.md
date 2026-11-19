# Backlog et ordre de réalisation
Les phases sont une séquence, pas un calendrier garanti. Aucune estimation de délai avant inspection technique.

| Phase | Livrable | Condition de sortie |
|---|---|---|
| 0 | Identification appareil, design de 3 écrans, sources démo | Direction visuelle validée ; matrice capacités créée |
| 1 | APK minimal, connexion, catalogue local | Authentification et mise à jour sans blocage |
| 2 | TMDB, fiches, premier moteur, reprise | Un film et un épisode réellement lus |
| 3 | Direct, favoris, EPG | Chaînes accessibles et couverture mesurée |
| 4 | LibVLC/Media3, instrumentation | Comparaison reproductible et une session active |
| 5 | Turbo, optimisation, intro | Gains de stabilité mesurés ou absence de gain documentée |
| 6 | Finition, protections, recette | APK et sources documentés ; limites explicites |
| Après MVP | Profils avancés, saisie téléphone, collections | Priorisation à partir de l’usage réel |

P0 : accès, lecture, navigation, persistance, sécurité, correspondances, EPG et erreurs.
P1 : deux moteurs, diagnostic, Turbo, polish, accessibilité, protection des catégories adultes.
P2 : enrichissements confort et personnalisation avancée. Le caractère MVP signifie une présence avant la livraison finale, pas une implémentation simultanée.

## Livrables du développeur
Sources avec README, versions fixées et Gradle wrapper adapté ; configuration exemple sans secrets ; APK debug compilé et checksum ; schéma local et migrations ; instructions installation/désinstallation ; recette et captures non sensibles ; liste des licences ; limites et fonctionnalités simulées ; PROJECT_STATE.md synthétique.
