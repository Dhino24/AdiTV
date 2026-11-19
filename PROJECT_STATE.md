# PROJECT_STATE — ADITV
_Mis à jour : 19/11/2026_

## Phase actuelle : 1 — terminée côté code, à valider sur la Stick
Direction visuelle validée (phase 0). Environnement cloud : `dl.google.com` et `gcp.mirrors.maven.org` autorisés.

### Livré
- Projet Android compilable : Kotlin, Compose for TV, Room, DataStore, WorkManager, OkHttp, Coil — `docs/VERSIONS_ET_LICENCES.md`.
- Connexion : adresse normalisée, test avant enregistrement, erreurs typées (accès refusé, compte inactif, délai, DNS, TLS, HTTP, réponse non Xtream), avertissement explicite avant tout HTTP en clair (autorisé pour cet hôte seulement), redirections vers un autre hôte refusées.
- Secrets : mot de passe chiffré AES-GCM (clé Android Keystore), sauvegardes Android désactivées, journaux masqués (`Redactor`).
- Catalogue : import progressif par lots (catégories puis Direct / Films / Séries), consultable pendant l'import, éléments absents marqués indisponibles (jamais supprimés), IDs locaux préfixés par le compte, actualisation ~12 h.
- Interface TV : menu latéral (mémoire de la rubrique active, → revient à l'élément quitté), accueil (ajouts récents déclarés, chaînes), Films/Séries/Direct (catégories + grille), fiche fournisseur, recherche locale, paramètres (statut, expiration, état de synchronisation, déconnexion avec effacement). Retour à la racine = double appui.
- Mode **Démo** fictif clairement identifié (pas de lien de lecture).
- 26 tests : redaction, nettoyage des titres, adresses, API Xtream simulée (MockWebServer), synchronisation, parcours UI complet à la télécommande (Robolectric, captures `design/captures-app/`).

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
- [ ] Installer `ADITV-0.1.0-debug.apk` (`docs/BUILD_ET_INSTALLATION.md`) et tester connexion + import avec le vrai abonnement.
- [ ] Communiquer modèle de Stick + version Fire OS, et les éventuels messages d'erreur affichés.

## Prochaine étape : phase 2
TMDB (clé personnelle saisie dans l'app) + matching, fiches enrichies, Media3, lecture film/épisode, progression et reprise, Ma liste.
