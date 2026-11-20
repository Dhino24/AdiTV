package com.aditv.data.epg

import com.aditv.core.AppLog
import com.aditv.core.TitleNormalizer
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.db.EpgChannelEntity
import com.aditv.data.db.EpgMappingEntity
import com.aditv.data.db.EpgSourceEntity
import com.aditv.data.db.ProgrammeEntity
import com.aditv.data.provider.ProviderAdapter
import com.aditv.data.provider.ProviderError
import com.aditv.net.CleartextNotAllowedException
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Programme prêt à afficher : horaires UTC déjà décalés selon la source et la chaîne. */
data class Programme(val title: String, val description: String?, val category: String?, val startUtc: Long, val endUtc: Long) {
    fun isLive(now: Long) = now in startUtc until endUtc
}

data class Coverage(val channels: Int, val withGuideId: Int, val covered: Int)

/**
 * Guide des programmes. Priorité : XMLTV du fournisseur, puis sources XMLTV ajoutées.
 * Une chaîne n'utilise qu'une source (la plus prioritaire ayant des données) : jamais deux grilles mélangées.
 * Rien n'est inventé : sans données, la chaîne apparaît « sans guide ».
 */
class EpgRepository(
    private val db: AditvDatabase,
    private val providerClient: OkHttpClient,
    private val externalClient: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.live()
    private val mutex = Mutex()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
    private val _version = MutableStateFlow(0L)
    /** Incrémenté après chaque actualisation : les écrans relisent les programmes. */
    val version: StateFlow<Long> = _version

    suspend fun ensureProviderSource(accountId: String, label: String) {
        if (dao.sources(accountId).none { it.kind == "provider" }) {
            dao.insertSource(EpgSourceEntity(accountId = accountId, kind = "provider", url = null, label = label, priority = 0))
        }
    }

    suspend fun addUrlSource(accountId: String, url: String): Long {
        val prio = (dao.sources(accountId).maxOfOrNull { it.priority } ?: 0) + 1
        return dao.insertSource(EpgSourceEntity(accountId = accountId, kind = "url", url = url.trim(), label = url.trim().substringAfter("://").substringBefore('/'), priority = prio))
    }

    suspend fun removeSource(id: Long) { dao.deleteSourceProgrammes(id); dao.deleteEpgChannels(id); dao.deleteSource(id) }
    suspend fun setSourceOffset(source: EpgSourceEntity, minutes: Int) = dao.updateSource(source.copy(offsetMinutes = minutes))
    suspend fun setSourceEnabled(source: EpgSourceEntity, enabled: Boolean) = dao.updateSource(source.copy(enabled = enabled))

    /** Actualise toutes les sources actives (fenêtre −1 / +7 jours). Ne lève pas : l'erreur est rangée dans la source. */
    suspend fun refresh(accountId: String, adapter: ProviderAdapter, serverZone: String?) = mutex.withLock {
        val now = clock()
        val zone = runCatching { ZoneId.of(serverZone ?: DEFAULT_ZONE) }.getOrDefault(ZoneId.of(DEFAULT_ZONE))
        val wanted = dao.usedEpgIds(accountId).toSet()
        for (src in dao.sources(accountId).filter { it.enabled }) {
            _status.value = "Guide : ${src.label}…"
            val stamp = clock()
            try {
                val parser = XmltvParser(zone, now - DAY, now + 7 * DAY, wanted)
                val channels = ArrayList<EpgChannelEntity>()
                val batch = ArrayList<ProgrammeEntity>(1000)
                val stats = withContext(Dispatchers.IO) {
                    open(src, adapter, now).use { input ->
                        parser.parse(input,
                            onChannel = { c -> channels += EpgChannelEntity(src.id, accountId, c.id, c.displayName, TitleNormalizer.searchKey(c.displayName), c.iconUrl) },
                            onProgramme = { p ->
                                batch += ProgrammeEntity(0, accountId, src.id, p.channelId, p.startUtc, p.endUtc, p.title, p.description, p.category, p.episode, stamp)
                                if (batch.size >= 1000) { runBlocking { dao.insertProgrammes(batch.toList()) }; batch.clear() }
                            })
                    }
                }
                if (batch.isNotEmpty()) dao.insertProgrammes(batch)
                dao.deleteEpgChannels(src.id); dao.upsertEpgChannels(channels)
                // Remplacement seulement après un import complet : une panne ne vide pas le guide.
                dao.deleteStaleProgrammes(src.id, stamp)
                dao.updateSource(src.copy(lastFetchAt = clock(), lastError = null, programmeCount = dao.countProgrammes(src.id), channelCount = stats.channels, ambiguousTimes = stats.ambiguousTimes))
                AppLog.d("Guide ${src.label} : ${stats}")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val msg = when (e) {
                    is XmltvTooLargeException -> e.message
                    is ProviderError -> e.message
                    is CleartextNotAllowedException -> "HTTP en clair refusé pour ${e.host} (utilisez https://)."
                    is org.xmlpull.v1.XmlPullParserException -> "Fichier XMLTV illisible."
                    is IOException -> e.message?.takeIf { it.startsWith("HTTP") }?.let { "Source indisponible ($it)." } ?: "Téléchargement impossible (${e.javaClass.simpleName})."
                    else -> "Erreur (${e.javaClass.simpleName})."
                }
                AppLog.w("Guide ${src.label} en échec", e)
                dao.updateSource(src.copy(lastError = msg, lastFetchAt = clock()))
            }
        }
        dao.deleteOldProgrammes(now - DAY)
        _status.value = null
        _version.value = _version.value + 1
    }

    private fun open(src: EpgSourceEntity, adapter: ProviderAdapter, now: Long): java.io.InputStream {
        if (src.kind == "provider") adapter.inlineXmltv(now)?.let { return it.byteInputStream() }
        val url = if (src.kind == "provider") adapter.xmltvUrl() ?: throw IOException("Pas de guide XMLTV pour ce fournisseur.") else src.url!!
        val client = if (src.kind == "provider") providerClient else externalClient
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        if (!resp.isSuccessful) { resp.close(); throw if (src.kind == "provider") ProviderError.HttpStatus(resp.code) else IOException("HTTP ${resp.code}") }
        return resp.body.byteStream()
    }

    /** Identifiant de guide effectif d'une chaîne (association manuelle prioritaire). */
    suspend fun effectiveId(item: CatalogItemEntity): String? {
        val m = dao.mapping(item.id)
        return when { m?.disabled == true -> null; m?.epgChannelId != null -> m.epgChannelId; else -> item.epgChannelId }
    }

    /** Programmes par chaîne (clé = itemId) sur une fenêtre, décalages appliqués, une seule source par chaîne. */
    /** Calcul hors du fil de l'interface (sur la Stick, des milliers de programmes figeaient l'écran plusieurs secondes). */
    suspend fun programmesFor(accountId: String, items: List<Pair<String, String?>>, from: Long, to: Long): Map<String, List<Programme>> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { programmesForImpl(accountId, items, from, to) }

    private suspend fun programmesForImpl(accountId: String, items: List<Pair<String, String?>>, from: Long, to: Long): Map<String, List<Programme>> {
        val mappings = dao.mappings(accountId).associateBy { it.itemId }
        val ids = items.mapNotNull { (itemId, epg) ->
            val m = mappings[itemId]
            when { m?.disabled == true -> null; m?.epgChannelId != null -> itemId to m.epgChannelId; epg != null -> itemId to epg; else -> null }
        }
        if (ids.isEmpty()) return emptyMap()
        val sources = dao.sources(accountId).associateBy { it.id }
        // Marge pour les décalages horaires des sources (quelques heures au plus).
        val margin = 4 * HOUR
        val rows = ids.map { it.second }.distinct().chunked(500).flatMap { dao.programmesIn(accountId, it, from - margin, to + margin) }
        val byChannel = rows.groupBy { it.channelId }
        return ids.associate { (itemId, epg) ->
            val all = byChannel[epg].orEmpty()
            val bestSource = all.map { it.sourceId }.distinct().minByOrNull { sources[it]?.priority ?: Int.MAX_VALUE }
            val shift = ((sources[bestSource]?.offsetMinutes ?: 0) + (mappings[itemId]?.offsetMinutes ?: 0)) * 60_000L
            itemId to all.filter { it.sourceId == bestSource }
                .map { Programme(it.title, it.description, it.category, it.startUtc + shift, it.endUtc + shift) }
                .filter { it.endUtc > from && it.startUtc < to }
        }
    }

    suspend fun nowNext(accountId: String, item: CatalogItemEntity, now: Long = clock()): List<Programme> =
        programmesFor(accountId, listOf(item.id to item.epgChannelId), now, now + 6 * HOUR)[item.id].orEmpty().take(2)

    suspend fun coverage(accountId: String): Coverage {
        val now = clock()
        val channels = db.catalog().allChannels(accountId)
        val mappings = dao.mappings(accountId).associateBy { it.itemId }
        val effective = channels.mapNotNull { c -> val m = mappings[c.id]; if (m?.disabled == true) null else m?.epgChannelId ?: c.epgChannelId }
        val covered = dao.coveredChannelIds(accountId, now).toSet()
        return Coverage(channels.size, effective.size, effective.count { it in covered })
    }

    suspend fun setManualMapping(item: CatalogItemEntity, epgChannelId: String?, disabled: Boolean = false) {
        val prev = dao.mapping(item.id)
        dao.upsertMapping(EpgMappingEntity(item.id, item.accountId, epgChannelId, manual = true, offsetMinutes = prev?.offsetMinutes ?: 0, disabled = disabled))
    }

    suspend fun setChannelOffset(item: CatalogItemEntity, minutes: Int) {
        val prev = dao.mapping(item.id)
        dao.upsertMapping((prev ?: EpgMappingEntity(item.id, item.accountId, null, manual = false)).copy(offsetMinutes = minutes))
    }

    suspend fun resetMapping(itemId: String) = dao.deleteMapping(itemId)

    suspend fun lastRefresh(accountId: String): Long? = dao.sources(accountId).mapNotNull { it.lastFetchAt }.maxOrNull()

    suspend fun channelsFirst(accountId: String) = db.catalog().allChannels(accountId)

    suspend fun accountSources(accountId: String) = dao.observeSources(accountId).first()

    companion object {
        const val DEFAULT_ZONE = "Europe/Paris"
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
