package com.aditv.data.library

import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.EpisodeEntity
import com.aditv.data.db.WatchProgressEntity
import com.aditv.data.db.WatchlistEntity
import com.aditv.data.provider.ProviderAdapter
import com.aditv.data.provider.ProviderError
import com.aditv.data.sync.CatalogMapping
import com.aditv.data.provider.MediaType

/** Épisodes, progression et « Ma liste ». Tout est local et propre au compte. */
class LibraryRepository(private val db: AditvDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.library()

    fun episodeId(accountId: String, providerEpisodeId: String) = "$accountId|EPISODE|$providerEpisodeId"

    /** Recharge la liste d'épisodes depuis le fournisseur. En cas d'échec, la liste en cache reste affichée. */
    suspend fun refreshEpisodes(accountId: String, seriesItemId: String, adapter: ProviderAdapter): Result<Int> = runCatching {
        val providerSeriesId = seriesItemId.substringAfterLast('|')
        val raw = adapter.seriesEpisodes(providerSeriesId)
        val stamp = clock()
        val entities = raw.mapIndexed { i, r ->
            EpisodeEntity(
                id = episodeId(accountId, r.providerId), accountId = accountId, seriesItemId = seriesItemId,
                providerId = r.providerId, season = r.season, number = r.number, title = r.title, plot = r.plot,
                imageUrl = r.imageUrl, durationSec = r.durationSec, containerExt = r.containerExt, airDate = r.airDate,
                position = i, lastSeenAt = stamp,
            )
        }
        // Une réponse vide n'efface pas un cache existant (panne probable plutôt que série vidée).
        if (entities.isNotEmpty()) dao.replaceEpisodes(seriesItemId, entities, stamp)
        entities.size
    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }

    suspend fun saveProgress(accountId: String, contentId: String, kind: String, seriesItemId: String?, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 && positionMs <= 0) return
        dao.upsertProgress(
            WatchProgressEntity(
                accountId, contentId, kind, seriesItemId, positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0), clock(),
                ProgressPolicy.isCompleted(positionMs, durationMs),
            )
        )
    }

    suspend fun progress(accountId: String, contentId: String) = dao.progress(accountId, contentId)

    /** Épisode suivant dans l'ordre fournisseur (saison puis numéro), sans en inventer. */
    suspend fun nextEpisode(seriesItemId: String, currentEpisodeId: String): EpisodeEntity? {
        val list = dao.episodesNow(seriesItemId)
        val i = list.indexOfFirst { it.id == currentEpisodeId }
        return if (i >= 0) list.getOrNull(i + 1) else null
    }

    /** Épisode à proposer : celui en cours, sinon le suivant du dernier terminé, sinon le premier. */
    suspend fun episodeToResume(accountId: String, seriesItemId: String): Pair<EpisodeEntity, Long?>? {
        val list = dao.episodesNow(seriesItemId).ifEmpty { return null }
        val last = dao.lastForSeries(accountId, seriesItemId) ?: return list.first() to null
        val current = list.firstOrNull { it.id == last.contentId } ?: return list.first() to null
        return if (last.completed) (list.getOrNull(list.indexOf(current) + 1) ?: current) to null
        else current to ProgressPolicy.resumePosition(last.positionMs, last.durationMs, false)
    }

    suspend fun toggleWatchlist(accountId: String, itemId: String, add: Boolean) =
        if (add) dao.addToWatchlist(WatchlistEntity(accountId, itemId, clock())) else dao.removeFromWatchlist(accountId, itemId)

    /** Retire un élément de « Reprendre » (historique effaçable). */
    suspend fun forget(accountId: String, itemId: String) = dao.clearProgressFor(accountId, itemId)

    companion object {
        fun movieItemId(accountId: String, providerId: String) = CatalogMapping.itemId(accountId, MediaType.MOVIE, providerId)
        fun isAuthError(e: Throwable) = e is ProviderError.InvalidCredentials || e is ProviderError.AccountInactive
    }
}
