package com.aditv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Épisode tel que déclaré par le fournisseur. Aucun épisode n'est créé à partir de TMDB. */
@Entity(tableName = "episodes", indices = [Index("seriesItemId", "season", "number")])
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val seriesItemId: String,
    val providerId: String,
    val season: Int,
    val number: Int?,
    val title: String,
    val plot: String?,
    val imageUrl: String?,
    val durationSec: Int?,
    val containerExt: String?,
    val airDate: String?,
    val position: Int,
    val lastSeenAt: Long,
)

/** Progression par compte et par contenu (film ou épisode). Le direct n'a pas de progression. */
@Entity(tableName = "watch_progress", primaryKeys = ["accountId", "contentId"], indices = [Index("accountId", "updatedAt")])
data class WatchProgressEntity(
    val accountId: String,
    val contentId: String,
    val kind: String,
    /** Pour un épisode : la série (catalog_items.id). */
    val seriesItemId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean,
)

/** « Ma liste » : films et séries suivis, distincte des chaînes favorites et de l'historique. */
@Entity(tableName = "watchlist", primaryKeys = ["accountId", "itemId"])
data class WatchlistEntity(val accountId: String, val itemId: String, val addedAt: Long)

/** Correspondance catalogue → TMDB. Un choix manuel est verrouillé et survit aux synchronisations. */
@Entity(tableName = "metadata_match", indices = [Index("accountId")])
data class MetadataMatchEntity(
    @PrimaryKey val itemId: String,
    val accountId: String,
    /** movie | tv */
    val mediaType: String,
    val tmdbId: Int?,
    /** confirmed | ambiguous | unmatched */
    val status: String,
    val confidence: Double,
    /** provider | automatic | manual */
    val origin: String,
    val locked: Boolean,
    /** Candidats proposés pour une correction (JSON), si ambigu. */
    val candidatesJson: String?,
    val updatedAt: Long,
)

/** Métadonnées TMDB en cache (partagées entre comptes : ce sont des données publiques). */
@Entity(tableName = "tmdb_metadata", primaryKeys = ["mediaType", "tmdbId"])
data class TmdbMetadataEntity(
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val originalTitle: String?,
    val overview: String?,
    /** Langue réelle du résumé (fr-FR ou repli en-US). */
    val overviewLanguage: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val runtimeMin: Int?,
    val genres: String?,
    val voteAverage: Double?,
    val voteCount: Int?,
    /** Distribution : « Nom|Rôle » séparés par des retours ligne. */
    val cast: String?,
    val crew: String?,
    val seasonCount: Int?,
    /** IDs TMDB similaires / recommandés, séparés par des virgules. */
    val relatedIds: String?,
    val fetchedAt: Long,
)

/** Élément « Reprendre » : contenu, titre à afficher, progression. */
data class ResumeRow(
    val contentId: String,
    val kind: String,
    val itemId: String,
    val title: String,
    val episodeLabel: String?,
    val imageUrl: String?,
    val tmdbPoster: String?,
    val tmdbBackdrop: String?,
    val positionMs: Long,
    val durationMs: Long,
)
