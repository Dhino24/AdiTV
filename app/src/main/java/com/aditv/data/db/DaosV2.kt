package com.aditv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    // ---------- épisodes ----------
    @Upsert suspend fun upsertEpisodes(e: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE seriesItemId = :seriesItemId AND lastSeenAt <> :stamp")
    suspend fun deleteStaleEpisodes(seriesItemId: String, stamp: Long)

    @Transaction
    suspend fun replaceEpisodes(seriesItemId: String, episodes: List<EpisodeEntity>, stamp: Long) {
        upsertEpisodes(episodes)
        // Les épisodes retirés disparaissent de la liste ; leur progression reste dans watch_progress.
        deleteStaleEpisodes(seriesItemId, stamp)
    }

    @Query("SELECT * FROM episodes WHERE seriesItemId = :seriesItemId ORDER BY season, number IS NULL, number, position")
    fun episodes(seriesItemId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesItemId = :seriesItemId ORDER BY season, number IS NULL, number, position")
    suspend fun episodesNow(seriesItemId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id") suspend fun episode(id: String): EpisodeEntity?

    // ---------- progression ----------
    @Upsert suspend fun upsertProgress(p: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId AND contentId = :contentId")
    suspend fun progress(accountId: String, contentId: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId AND seriesItemId = :seriesItemId")
    fun progressForSeries(accountId: String, seriesItemId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId AND contentId = :contentId")
    fun observeProgress(accountId: String, contentId: String): Flow<WatchProgressEntity?>

    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId AND seriesItemId = :seriesItemId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun lastForSeries(accountId: String, seriesItemId: String): WatchProgressEntity?

    /**
     * « Reprendre » : un élément par film ou par série (le plus récent), non terminé,
     * dont le contenu est encore disponible chez le fournisseur.
     */
    @Query(
        """SELECT wp.contentId, wp.kind,
                  COALESCE(wp.seriesItemId, wp.contentId) AS itemId,
                  COALESCE(tm.title, ci.title) AS title,
                  CASE WHEN ep.id IS NOT NULL THEN 'S' || ep.season || ' É' || COALESCE(ep.number, '?') || ' · ' || ep.title END AS episodeLabel,
                  COALESCE(ep.imageUrl, ci.imageUrl) AS imageUrl,
                  tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop,
                  wp.positionMs, wp.durationMs
           FROM watch_progress wp
           JOIN catalog_items ci ON ci.id = COALESCE(wp.seriesItemId, wp.contentId)
           LEFT JOIN episodes ep ON ep.id = wp.contentId
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE wp.accountId = :accountId AND wp.completed = 0 AND ci.available = 1
             AND wp.updatedAt = (SELECT MAX(w2.updatedAt) FROM watch_progress w2
                                 WHERE w2.accountId = wp.accountId AND COALESCE(w2.seriesItemId, w2.contentId) = COALESCE(wp.seriesItemId, wp.contentId))
           ORDER BY wp.updatedAt DESC LIMIT :limit"""
    )
    fun resumeRows(accountId: String, limit: Int): Flow<List<ResumeRow>>

    @Query("DELETE FROM watch_progress WHERE accountId = :accountId AND (contentId = :itemId OR seriesItemId = :itemId)")
    suspend fun clearProgressFor(accountId: String, itemId: String)

    // ---------- ma liste ----------
    @Upsert suspend fun addToWatchlist(w: WatchlistEntity)
    @Query("DELETE FROM watchlist WHERE accountId = :accountId AND itemId = :itemId") suspend fun removeFromWatchlist(accountId: String, itemId: String)
    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE accountId = :accountId AND itemId = :itemId)") fun inWatchlist(accountId: String, itemId: String): Flow<Boolean>

    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM watchlist w JOIN catalog_items ci ON ci.id = w.itemId
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE w.accountId = :accountId AND ci.available = 1
           ORDER BY w.addedAt DESC"""
    )
    fun watchlist(accountId: String): Flow<List<CatalogCard>>

    // ---------- nettoyage du compte ----------
    @Query("DELETE FROM episodes WHERE accountId = :accountId") suspend fun deleteEpisodes(accountId: String)
    @Query("DELETE FROM watch_progress WHERE accountId = :accountId") suspend fun deleteProgress(accountId: String)
    @Query("DELETE FROM watchlist WHERE accountId = :accountId") suspend fun deleteWatchlist(accountId: String)
}

@Dao
interface MetadataDao {
    @Upsert suspend fun upsertMatch(m: MetadataMatchEntity)
    @Query("SELECT * FROM metadata_match WHERE itemId = :itemId") suspend fun match(itemId: String): MetadataMatchEntity?
    @Query("SELECT * FROM metadata_match WHERE itemId = :itemId") fun observeMatch(itemId: String): Flow<MetadataMatchEntity?>

    @Upsert suspend fun upsertMetadata(m: TmdbMetadataEntity)
    @Query("SELECT * FROM tmdb_metadata WHERE mediaType = :mediaType AND tmdbId = :tmdbId") suspend fun metadata(mediaType: String, tmdbId: Int): TmdbMetadataEntity?
    @Query("SELECT * FROM tmdb_metadata WHERE mediaType = :mediaType AND tmdbId = :tmdbId") fun observeMetadata(mediaType: String, tmdbId: Int): Flow<TmdbMetadataEntity?>

    /** Éléments du catalogue dont la correspondance confirmée figure dans la liste d'IDs TMDB (suggestions disponibles). */
    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM metadata_match mm JOIN catalog_items ci ON ci.id = mm.itemId
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE mm.accountId = :accountId AND mm.status = 'confirmed' AND mm.mediaType = :mediaType
             AND mm.tmdbId IN (:tmdbIds) AND ci.available = 1 AND ci.id <> :excludeId
           LIMIT 20"""
    )
    suspend fun availableAmong(accountId: String, mediaType: String, tmdbIds: List<Int>, excludeId: String): List<CatalogCard>

    /** Éléments à enrichir en priorité : ajouts récents sans correspondance. */
    @Query(
        """SELECT ci.id FROM catalog_items ci LEFT JOIN metadata_match mm ON mm.itemId = ci.id
           WHERE ci.accountId = :accountId AND ci.type IN ('MOVIE','SERIES') AND ci.available = 1 AND mm.itemId IS NULL
           ORDER BY ci.addedAt IS NULL, ci.addedAt DESC LIMIT :limit"""
    )
    suspend fun unmatchedRecent(accountId: String, limit: Int): List<String>

    /** Fiches confirmées mises en cache sans information de logo (avant 0.4) : à recharger. */
    @Query(
        """SELECT mm.itemId FROM metadata_match mm JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE mm.accountId = :accountId AND mm.status = 'confirmed' AND tm.hasLogoInfo = 0 LIMIT :limit"""
    )
    suspend fun itemsMissingLogoInfo(accountId: String, limit: Int): List<String>

    @Query("DELETE FROM metadata_match WHERE accountId = :accountId") suspend fun deleteMatches(accountId: String)
    @Query("DELETE FROM metadata_match WHERE accountId = :accountId AND locked = 0") suspend fun deleteAutomaticMatches(accountId: String)
    @Query("DELETE FROM tmdb_metadata") suspend fun clearMetadataCache()
}
