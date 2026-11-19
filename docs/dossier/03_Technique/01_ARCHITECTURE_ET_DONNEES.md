# Architecture proposée
## Structure légère
Kotlin et Compose adapté TV. Un module app au départ, packages par responsabilité ; extraire des modules seulement si utile. UI → ViewModel/état → cas d’usage légers → repositories → API/DB. Coroutines pour I/O et parsing. Room pour catalogue/EPG/progression ; DataStore pour préférences ; couche chiffrée/Keystore pour secrets ; tâches différées compatibles Fire OS. Choisir et figer les versions au début du développement.

Interfaces : ProviderRepository, CatalogRepository, MetadataRepository, EpgRepository, PlaybackController, PlaybackMetrics, PreferencesRepository. Utiliser un adaptateur démo et un réel explicitement identifiés. Aucun backend obligatoire pour le MVP personnel.

## Entités et identités
- ProviderAccount : UUID local, endpoint canonique, type, référence secret ; jamais mot de passe en clair dans Room.
- CatalogItem : UUID local + accountId + providerItemId + type + titre source + titre normalisé + catégories + disponibilité + lastSeenAt + addedAt fournisseur si fiable.
- StreamVariant : référence URL sensible, paramètres d’accès, résolution/pistes connues uniquement de la source. Ne pas persister inutilement des liens expirables.
- MetadataMatch : itemId, mediaType, tmdbId, statut pending/confirmed/ambiguous/unmatched, confiance, origine provider/automatic/manual, date et verrou manuel.
- SeriesSeason/Episode : ID fournisseur stable, saison/épisode si fiable, liaison metadata conditionnelle.
- Channel : compte, ID fournisseur, EPG ID, ordre, groupes, logo source.
- EpgSource, ChannelEpgMapping, Programme : début/fin UTC, identifiant de source, langue, mise à jour ; clé de déduplication.
- Profile, FavoriteChannel, WatchlistEntry, WatchProgress : clés par compte/profil/item ; position/durée/horodatage.
- PlaybackSession : ID éphémère, moteur, agrégats locaux sans secrets ; conservation courte configurable.

Index : (accountId,type,category), titres recherchables, (channelId,startUtc,endUtc). Migrations testées. Mise à jour par lots transactionnels et marque de fraîcheur : ne pas supprimer tout le catalogue avant de recevoir le nouveau. Ne pas effacer progression/favoris si un titre disparaît temporairement. IDs fournisseur non uniques entre comptes.

## Import et cache
Récupérer auth/capacités, catégories puis listes progressivement ; détails séries à la demande ; EPG par fenêtres ; TMDB priorisé sur éléments visibles. Cache images avec quota disque et taille adaptée. Rechercher localement sans requête serveur à chaque frappe. Éviter le recyclage de toutes les cartes lors d’une mise à jour ; clés stables. Invalidation explicite et affichage date de synchronisation.

## Fournisseur
Le format URL/user/password suggère Xtream mais ne prouve pas une compatibilité. Isoler les différences de schémas, dates, encodages et comportements par adaptateur. Timeouts distincts connexion/lecture, erreurs HTTP et parse distinguées. Endpoint, schéma et limites doivent être vérifiés auprès du fournisseur ; ne pas prendre une API communautaire pour une spécification officielle.
