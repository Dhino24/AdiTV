package com.aditv.data.tmdb

import com.aditv.core.AppLog
import com.aditv.core.TitleNormalizer
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogCard
import com.aditv.data.db.TrendingEntity
import com.aditv.data.variants.VariantPrefs
import com.aditv.data.variants.collapsed
import kotlin.math.abs

/** Titre du Top 10 : rang dans l'abonnement, rang TMDB d'origine, carte du catalogue (meilleure version). */
data class TopEntry(val rank: Int, val tmdbRank: Int, val card: CatalogCard, val poster: String?, val backdrop: String?)

/**
 * Top 10 « du moment » : tendances TMDB de la semaine (popularité réelle, source affichée),
 * limitées aux titres réellement présents dans l'abonnement. Rien n'est inventé ni classé à la main.
 */
class TrendingRepository(private val db: AditvDatabase, private val tmdb: TmdbClient, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.sport()

    /** Rafraîchit les tendances au plus toutes les 12 h (sans clé TMDB : rien). */
    suspend fun refreshIfStale(force: Boolean = false) {
        for (type in listOf("movie", "tv")) {
            val at = dao.trendingFetchedAt(type)
            if (!force && at != null && clock() - at < 12 * 3_600_000L) continue
            runCatching {
                val now = clock()
                val list = (1..3).flatMap { page -> tmdb.trending(type, page) }.distinctBy { it.id }
                if (list.isEmpty()) return@runCatching
                dao.clearTrending(type)
                dao.insertTrending(list.mapIndexed { i, c -> TrendingEntity(type, i + 1, c.id, c.title, c.originalTitle, c.year, c.posterPath, c.backdropPath, now) })
            }.onFailure { e -> if (e !is TmdbError.NoKey) AppLog.w("Tendances TMDB indisponibles", e) }
        }
    }

    /** Les 10 premières tendances présentes dans l'abonnement, dans l'ordre TMDB. */
    /** Dernier résultat par type : rouvrir l'accueil ne refait pas le calcul si rien n'a changé. */
    private val memo = java.util.concurrent.ConcurrentHashMap<String, Pair<Any, List<TopEntry>>>()

    suspend fun top(accountId: String, mediaType: String, trending: List<TrendingEntity>, prefs: VariantPrefs, size: Int = 10, catalogStamp: Long = 0): List<TopEntry> {
        if (trending.isEmpty()) return emptyList()
        val key = listOf(trending.map { it.tmdbId }, trending.firstOrNull()?.fetchedAt, prefs, size, catalogStamp)
        memo["$accountId|$mediaType"]?.takeIf { it.first == key }?.let { return it.second }
        return compute(accountId, mediaType, trending, prefs, size).also { memo["$accountId|$mediaType"] = key to it }
    }

    private suspend fun compute(accountId: String, mediaType: String, trending: List<TrendingEntity>, prefs: VariantPrefs, size: Int): List<TopEntry> {
        val type = if (mediaType == "movie") "MOVIE" else "SERIES"
        // 1) Titres déjà reconnus par TMDB dans le catalogue.
        val matched = trending.map { it.tmdbId }.chunked(200).flatMap { ids -> db.metadata().availableAmongAll(accountId, mediaType, ids) }
        val byTmdb = matched.groupBy { it.tmdbId }.mapValues { (_, v) -> v.map { it.card }.collapsed(prefs).first().first }
        val out = ArrayList<TopEntry>()
        val used = HashSet<String>()
        for (t in trending) {
            if (out.size >= size) break
            // 2) Sinon : même titre (français ou original) et année proche dans le catalogue du fournisseur.
            val card = byTmdb[t.tmdbId] ?: findByTitle(accountId, type, t, prefs) ?: continue
            if (!used.add(card.variantKey ?: card.id)) continue
            out += TopEntry(out.size + 1, t.rank, card, t.posterPath, t.backdropPath)
        }
        return out
    }

    private suspend fun findByTitle(accountId: String, type: String, t: TrendingEntity, prefs: VariantPrefs): CatalogCard? {
        for (title in listOfNotNull(t.title, t.originalTitle).distinct()) {
            val key = TitleNormalizer.searchKey(title)
            if (key.length < 2) continue
            // Recherche exacte par l'index (avant : recherche « contient » = parcours de tout le catalogue, ×60 titres).
            val hits = db.catalog().byExactKey(accountId, type, key).filter { c ->
                t.year == null || c.year == null || abs(c.year - t.year) <= 1
            }
            if (hits.isNotEmpty()) return hits.collapsed(prefs).first().first
        }
        return null
    }
}
