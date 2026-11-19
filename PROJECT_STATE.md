# PROJECT_STATE — ADITV
_Mis à jour : 19/11/2026_

## Phase actuelle : 1 — terminée côté code (0.1.1), à valider sur la Stick
Direction visuelle validée (phase 0). Environnement cloud : `dl.google.com` et `gcp.mirrors.maven.org` autorisés.

### Appareil cible (confirmé le 19/11/2026)
Fire TV Stick 4K **1re génération**, Fire OS **6.7.1.1** = Android 7.1 (API 25), espace utilisateur 32 bits (ARMv7).
Stockage : ~1 Go libre sur 5,28 Go → cache d'images limité à 5 % de l'espace libre (16–64 Mo) ; LibVLC (phase 4) devra rester en ARMv7 seul.
Capacités annoncées par Amazon pour ce modèle (HEVC, VP9, HDR10, HDR10+, Dolby Vision, Dolby Atmos) : **à mesurer sur l'appareil**, aucune n'est supposée acquise.

### Correctif 0.1.1
- 0.1.0 utilisait `java.time` (Android 8+) : **plantage probable sur Fire OS 6** à l'affichage des dates. Corrigé par le désucrage des bibliothèques Java ; le lint bloque désormais tout appel d'API postérieur à Android 7.1.
- APK limité à ARMv7, parcours UI rejoué aussi en API 25 (Robolectric).

### Livré
- Projet Android compilable : Kotlin, Compose for TV, Room, DataStore, WorkManager, OkHttp, Coil — `docs/VERSIONS_ET_LICENCES.md`.
- Connexion : adresse normalisée, test avant enregistrement, erreurs typées (accès refusé, compte inactif, délai, DNS, TLS, HTTP, réponse non Xtream), avertissement explicite avant tout HTTP en clair (autorisé pour cet hôte seulement), redirections vers un autre hôte refusées.
- Secrets : mot de passe chiffré AES-GCM (clé Android Keystore), sauvegardes Android désactivées, journaux masqués (`Redactor`).
- Catalogue : import progressif par lots (catégories puis Direct / Films / Séries), consultable pendant l'import, éléments absents marqués indisponibles (jamais supprimés), IDs locaux préfixés par le compte, actualisation ~12 h.
- Interface TV : menu latéral (mémoire de la rubrique active, → revient à l'élément quitté), accueil (ajouts récents déclarés, chaînes), Films/Séries/Direct (catégories + grille), fiche fournisseur, recherche locale, paramètres (statut, expiration, état de synchronisation, déconnexion avec effacement). Retour à la racine = double appui.
- Mode **Démo** fictif clairement identifié (pas de lien de lecture).
- 27 tests : redaction, nettoyage des titres, adresses, API Xtream simulée (MockWebServer), synchronisation, parcours UI complet à la télécommande (Robolectric, captures `design/captures-app/`).

### Réel / simulé
| Élément | État |
|---|---|
| API Xtream | Implémentée et testée contre un serveur simulé — **jamais testée avec ton fournisseur** |
| Interface | Testée en JVM (Robolectric 1920×1080) — **pas encore sur Fire TV** (fluidité, IME Amazon, télécommande physique) |
| Lecture, TMDB, EPG, Ma liste | Non commencés (écrans marqués « phase 2 / 3 ») |

### Limites connues
- Une seule catégorie par élément (la principale) ; les catégories multiples Xtream sont ignorées.
- Pas de pagination base de données : chaque catégorie est chargée en entier (à mesurer sur un gros catalogue).
- Les « ajouts récents » reposent sur la date d'ajout déclarée par le fournisseur.

## À faire par Adama
- [x] Modèle et Fire OS communiqués.
- [ ] Installer `ADITV-0.1.1-debug.apk` (pas la 0.1.0) (`docs/BUILD_ET_INSTALLATION.md`) et tester connexion + import avec le vrai abonnement.
- [ ] Communiquer les éventuels messages d'erreur affichés et l'impression de fluidité.

## Prochaine étape : phase 2
TMDB (clé personnelle saisie dans l'app) + matching, fiches enrichies, Media3, lecture film/épisode, progression et reprise, Ma liste.
