# PROJECT_STATE — ADITV
_Mis à jour : 19/11/2026_

## Appareil cible (confirmé)
Fire TV Stick 4K **1re génération**, Fire OS **6.7.1.1** = Android 7.1 (API 25), ARMv7 32 bits, ~1 Go de stockage libre.
Codecs/HDR annoncés par Amazon : **à mesurer**, rien n'est supposé acquis. Le lint bloque tout appel d'API postérieur à Android 7.1.

## Phase actuelle : 2 — terminée côté code (0.2.0), lecture à valider sur la Stick
0.1.1 installée par Adama sur la Stick (installation OK).

### Livré en phase 2
- **Lecture Media3** : films, épisodes, chaînes en direct. URL construite à la demande depuis les accès chiffrés (jamais stockée ni journalisée). Une seule session active dans toute l'app (ouvrir un contenu libère le précédent). Pistes audio / sous-titres **réellement présentes** dans le flux. Erreurs traduites prudemment (accès refusé / limite de connexions possible, flux introuvable, format non décodable…), « Réessayer » borné.
- **Télécommande dans le lecteur** : OK affiche les commandes ; ←/→ = −/+10 s en VOD (jamais en direct) ; touches lecture/pause, avance et retour rapides (±30 s) ; Retour ferme le panneau puis quitte.
- **Progression et reprise** : sauvegarde toutes les 10 s, à la pause, à la sortie et à la mise en veille (session libérée, réouverture en pause au réveil, pas de seconde session). Terminé à 95 % ou moins de 3 min restantes. « Reprendre » sur l'accueil (un élément par film/série), « Reprendre » / « Depuis le début » sur la fiche.
- **Séries** : saisons et épisodes du fournisseur (`get_series_info`), chargés à l'ouverture, cache conservé si le serveur échoue ; épisode suivant proposé à la fin (10 s, annulable).
- **TMDB** : clé personnelle (v3 ou jeton v4) saisie et testée dans Paramètres, chiffrée. Titre FR, résumé FR avec repli anglais signalé, affiche/fond à taille adaptée, genres, durée, note avec nombre de votes et source, distribution, réalisation, nombre de saisons. Correspondance : ID TMDB fourni **vérifié**, sinon titre nettoyé + année + type avec score ; remakes/homonymes sans année → **ambigu**, jamais validé au hasard. **Correction manuelle** (recherche, choix, « aucune correspondance »), verrouillée et conservée. Suggestions « Disponibles dans votre abonnement » = similaires TMDB ∩ catalogue confirmé. Enrichissement de fond des ajouts récents, espacé, backoff 429/5xx. TMDB en panne = fiches fournisseur, rien ne bloque.
- **Ma liste** : ajout/retrait depuis la fiche, écran dédié, rangée sur l'accueil.
- **Base v2** : migration automatique 1→2 testée (compte et catalogue conservés lors de la mise à jour 0.1.1 → 0.2.0).
- **47 tests** : matching (remakes, VOSTFR, titre original, suites, mauvaise année), client TMDB simulé (clé v3/v4, repli anglais, 401, 429), épisodes Xtream, URLs de flux et masquage, règles de reprise, migration, session unique Media3, verrou manuel, parcours télécommande complet en API 25 et 35.

### Réel / simulé
| Élément | État |
|---|---|
| Lecture film / épisode / direct | Code complet, session unique testée — **jamais lue sur la Stick ni avec ton abonnement** |
| TMDB | Testé contre un serveur simulé — **jamais avec une vraie clé** |
| Interface | Testée en JVM (Robolectric) — fluidité réelle à juger sur la Stick |

### Limites connues
- Bandes-annonces non intégrées (pas de lecteur YouTube sur la Stick sans application tierce) : reportées.
- Pas de zapping ↑/↓ en direct ni de guide (phase 3). Pas de choix de moteur (LibVLC, phase 4).
- Une catégorie par élément ; chaque catégorie chargée en entier (à mesurer sur un gros catalogue).
- Les journaux internes de Media3 sont coupés en release ; en debug ils restent actifs (niveau avertissement).

## À faire par Adama
- [ ] Installer `ADITV-0.2.0-debug.apk` par-dessus la 0.1.1 (`adb install -r`, données conservées).
- [ ] Lire **un film**, **un épisode** et **une chaîne** ; noter les messages d'erreur éventuels et la fluidité.
- [ ] Optionnel : créer une clé TMDB et la saisir dans Paramètres → Métadonnées TMDB.

## Prochaine étape : phase 3
Direct : favoris, zapping, EPG (API fournisseur / XMLTV), grille télécommandable, maintenant/suivant dans le lecteur.
