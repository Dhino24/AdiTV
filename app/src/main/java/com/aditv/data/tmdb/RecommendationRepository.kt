package com.aditv.data.tmdb

import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogCard
import java.util.concurrent.ConcurrentHashMap

/** « Parce que vous avez regardé X » : titre de départ + recommandations disponibles dans l'abonnement. */
data class BecauseRow(val seedTitle: String, val mediaType: String, val cards: List<CatalogCard>)

/**
 * Recommandations personnelles : les 2 derniers titres regardés (plus de 5 min) par le profil, puis les
 * recommandations TMDB de chacun, en ne gardant que ce qui est DANS l'abonnement (déjà reconnu sur TMDB).
 * 1 requête TMDB par titre de départ, gardée 12 h en mémoire. Sans clé TMDB : rien.
 */
class RecommendationRepository(private val db: AditvDatabase, private val tmdb: TmdbClient, private val hasKey: () -> Boolean, private val clock: () -> Long = System::currentTimeMillis) {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<Int>>>()

    suspend fun becauseYouWatched(accountId: String, libraryKey: String, seeds: Int = 2): List<BecauseRow> {
        if (!hasKey()) return emptyList()
        val meta = db.metadata()
        return meta.recentlyWatchedTmdb(libraryKey, seeds).mapNotNull { w ->
            val k = "${w.mediaType}:${w.tmdbId}"
            val ids = cache[k]?.takeIf { clock() - it.first < 12 * 3_600_000L }?.second
                ?: runCatching { tmdb.recommendations(w.mediaType, w.tmdbId).map { it.id } }.getOrNull()?.also { cache[k] = clock() to it }
                ?: return@mapNotNull null
            if (ids.isEmpty()) return@mapNotNull null
            val order = ids.withIndex().associate { it.value to it.index }
            val cards = meta.availableAmongAll(accountId, w.mediaType, ids.take(40))
                .filter { it.tmdbId != w.tmdbId }
                .distinctBy { it.tmdbId }
                .sortedBy { order[it.tmdbId] ?: Int.MAX_VALUE }
                .map { it.card }.take(15)
            cards.takeIf { it.size >= 3 }?.let { BecauseRow(w.title, w.mediaType, it) }
        }
    }
}
