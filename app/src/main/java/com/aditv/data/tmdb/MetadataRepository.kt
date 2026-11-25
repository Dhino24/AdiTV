package com.aditv.data.tmdb

import com.aditv.core.AppLog
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.db.MetadataMatchEntity
import com.aditv.data.db.TmdbMetadataEntity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Correspondances catalogue → TMDB et cache des métadonnées.
 * Ordre : ID TMDB fourni (vérifié) → recherche titre nettoyé + année + type → ambigu / non trouvé.
 * Un choix manuel est verrouillé : aucune synchronisation ni enrichissement automatique ne le remplace.
 * Si TMDB est en panne, rien ne bloque : le catalogue et la lecture restent utilisables.
 */
class MetadataRepository(
    private val db: AditvDatabase,
    private val tmdb: TmdbClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.metadata()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var imageBase: String? = null

    suspend fun imageBase(): String = imageBase ?: runCatching { tmdb.imageBaseUrl() }.getOrDefault(TmdbClient.DEFAULT_IMAGE_BASE).also { imageBase = it }

    fun mediaTypeOf(item: CatalogItemEntity) = if (item.type == "SERIES") "tv" else "movie"

    /** Garantit une tentative de correspondance (dédupliquée). Renvoie l'erreur éventuelle sans la propager. */
    suspend fun ensure(itemId: String, force: Boolean = false): TmdbError? {
        val mine = CompletableDeferred<Unit>()
        val existing = inFlight.putIfAbsent(itemId, mine)
        if (existing != null) { existing.await(); return null }
        return try { resolve(itemId, force); null }
        catch (e: CancellationException) { throw e }
        catch (e: TmdbError) { AppLog.w("TMDB : ${e.message}"); e }
        catch (e: Exception) { AppLog.w("Enrichissement impossible", e); TmdbError.Parse() }
        finally { inFlight.remove(itemId); mine.complete(Unit) }
    }

    private suspend fun resolve(itemId: String, force: Boolean) {
        val item = db.catalog().observeItem(itemId).first() ?: return
        if (item.type == "LIVE") return
        val type = mediaTypeOf(item)
        val current = dao.match(itemId)
        if (current != null && !force) {
            if (current.locked || current.status != "confirmed") {
                current.tmdbId?.takeIf { current.status == "confirmed" }?.let { refreshIfStale(type, it) }
                return
            }
            current.tmdbId?.let { refreshIfStale(type, it) }
            return
        }
        if (current?.locked == true) { current.tmdbId?.let { fetchAndStore(type, it) }; return }

        // 1. ID fourni par le fournisseur : accepté seulement après vérification du type et du titre.
        val providerId = item.providerTmdbId?.toIntOrNull()
        if (providerId != null) {
            val details = runCatching { tmdb.details(type, providerId) }.getOrElse { if (it is TmdbError.NotFound) null else throw it }
            if (details != null) {
                val s = TmdbMatcher.score(item.title, item.year, details.candidate)
                if (s >= 0.6) {
                    store(details)
                    saveMatch(item, type, providerId, "confirmed", maxOf(s, 0.9), "provider", null)
                    return
                }
                AppLog.d("ID TMDB fourni incohérent avec le titre, recherche classique")
            }
        }
        // 2. Recherche par titre nettoyé + année.
        val candidates = tmdb.search(type, item.title, item.year)
        val d = TmdbMatcher.decide(item.title, item.year, candidates)
        val cands = d.ranked.take(6).map { it.first }
        when (d.status) {
            TmdbMatcher.Status.CONFIRMED -> {
                fetchAndStore(type, d.best!!.id)
                saveMatch(item, type, d.best.id, "confirmed", d.confidence, "automatic", null)
            }
            TmdbMatcher.Status.AMBIGUOUS -> saveMatch(item, type, null, "ambiguous", d.confidence, "automatic", cands)
            TmdbMatcher.Status.UNMATCHED -> saveMatch(item, type, null, "unmatched", d.confidence, "automatic", cands.ifEmpty { null })
        }
    }

    /** Choix manuel : verrouillé, prioritaire sur tout. */
    suspend fun chooseManually(itemId: String, mediaType: String, tmdbId: Int) {
        val item = db.catalog().observeItem(itemId).first() ?: return
        fetchAndStore(mediaType, tmdbId)
        saveMatch(item, mediaType, tmdbId, "confirmed", 1.0, "manual", null, locked = true)
    }

    /** « Aucune correspondance » choisi par l'utilisateur : on n'enrichit plus cet élément. */
    suspend fun rejectManually(itemId: String) {
        val item = db.catalog().observeItem(itemId).first() ?: return
        saveMatch(item, mediaTypeOf(item), null, "unmatched", 1.0, "manual", null, locked = true)
    }

    /** Retire le verrou et relance le matching automatique. */
    suspend fun resetToAutomatic(itemId: String): TmdbError? {
        dao.match(itemId)?.let { dao.upsertMatch(it.copy(locked = false, status = "unmatched", tmdbId = null)) }
        return ensure(itemId, force = true)
    }

    suspend fun searchCandidates(mediaType: String, query: String, year: Int?): List<TmdbCandidate> = tmdb.search(mediaType, query, year).take(12)

    fun candidatesOf(m: MetadataMatchEntity?): List<TmdbCandidate> =
        m?.candidatesJson?.let { runCatching { json.decodeFromString(ListSerializer(TmdbCandidate.serializer()), it) }.getOrNull() }.orEmpty()

    /** Enrichissement de fond des ajouts récents, espacé pour respecter TMDB ; s'arrête à la première erreur de clé. */
    suspend fun enrichRecent(accountId: String, limit: Int = 60): Int {
        var n = 0
        for (id in dao.unmatchedRecent(accountId, limit)) {
            val err = ensure(id)
            if (err is TmdbError.NoKey || err is TmdbError.InvalidKey) break
            n++
            delay(250)
        }
        return n
    }

    /** Recharge les fiches en cache d'avant 0.4 (sans logo-titre), espacées. */
    suspend fun refreshMissingLogos(accountId: String, limit: Int = 60) {
        for (itemId in dao.itemsMissingLogoInfo(accountId, limit)) {
            val m = dao.match(itemId) ?: continue
            val id = m.tmdbId ?: continue
            val err = runCatching { fetchAndStore(m.mediaType, id) }.exceptionOrNull()
            if (err is CancellationException) throw err
            if (err is TmdbError.NoKey || err is TmdbError.InvalidKey) break
            delay(250)
        }
    }

    private suspend fun refreshIfStale(type: String, id: Int) {
        val m = dao.metadata(type, id)
        if (m == null || clock() - m.fetchedAt > STALE_MS || m.cast.needsPhotos()) runCatching { fetchAndStore(type, id) }
            .onFailure { if (it is CancellationException) throw it } // cache périmé conservé si TMDB échoue
    }

    private suspend fun fetchAndStore(type: String, id: Int) = store(tmdb.details(type, id))

    private suspend fun store(d: TmdbDetails) {
        val c = d.candidate
        dao.upsertMetadata(
            TmdbMetadataEntity(
                mediaType = c.mediaType, tmdbId = c.id, title = c.title, originalTitle = c.originalTitle, overview = d.overview,
                overviewLanguage = d.overviewLanguage, posterPath = c.posterPath, backdropPath = d.backdropPath,
                releaseDate = d.releaseDate, runtimeMin = d.runtimeMin, genres = d.genres.joinToString(", ").ifEmpty { null },
                voteAverage = d.voteAverage, voteCount = d.voteCount,
                cast = d.cast.mapIndexed { i, (n, r) -> "$n|${r.orEmpty()}|${d.castPhotos.getOrNull(i).orEmpty()}" }.joinToString("\n").ifEmpty { null },
                crew = d.crew.joinToString(", ").ifEmpty { null }, seasonCount = d.seasonCount,
                relatedIds = d.relatedIds.joinToString(",").ifEmpty { null }, fetchedAt = clock(), trailerKey = d.trailerKey, logoPath = d.logoPath, hasLogoInfo = true,
            )
        )
    }

    private suspend fun saveMatch(
        item: CatalogItemEntity, type: String, tmdbId: Int?, status: String, confidence: Double, origin: String,
        candidates: List<TmdbCandidate>?, locked: Boolean = false,
    ) = dao.upsertMatch(
        MetadataMatchEntity(
            itemId = item.id, accountId = item.accountId, mediaType = type, tmdbId = tmdbId, status = status,
            confidence = confidence, origin = origin, locked = locked,
            candidatesJson = candidates?.let { json.encodeToString(ListSerializer(TmdbCandidate.serializer()), it) }, updatedAt = clock(),
        )
    )

    companion object { const val STALE_MS = 30L * 24 * 3600 * 1000 }
}

/** Distribution enregistrée avant l'ajout des portraits (« Nom|Rôle » sans 3e champ) : à recharger. */
internal fun String?.needsPhotos() = this != null && lineSequence().first().count { it == '|' } < 2
