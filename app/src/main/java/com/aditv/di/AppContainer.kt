package com.aditv.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.aditv.data.AccountRepository
import com.aditv.data.Session
import com.aditv.data.db.AditvDatabase
import com.aditv.data.prefs.AppPrefs
import com.aditv.data.sync.CatalogSync
import com.aditv.net.HttpClients
import com.aditv.security.SecretStore
import com.aditv.data.library.LibraryRepository
import com.aditv.data.tmdb.MetadataRepository
import com.aditv.data.tmdb.TmdbClient
import com.aditv.data.tmdb.TmdbKeyStore
import com.aditv.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

/**
 * Cache d'images : 5 % de l'espace libre, entre 16 et 64 Mo.
 * La Stick 4K 1re génération n'a que ~5 Go utilisables, souvent presque pleins.
 */
internal fun imageCacheBytes(context: Context): Long {
    val free = runCatching { android.os.StatFs(context.cacheDir.path).availableBytes }.getOrDefault(0L)
    return (free / 20).coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024)
}

const val CATALOG_PARSE_VERSION = 3

/** Injection manuelle : pas de framework DI pour un seul module. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = HttpClients()
    val db = AditvDatabase.build(context)
    val prefs = AppPrefs(context)
    val secrets = SecretStore(context)
    val accounts = AccountRepository(db, secrets, prefs, http)
    val sync = CatalogSync(db.catalog())
    val library = LibraryRepository(db)
    val tmdbKey = TmdbKeyStore(secrets)
    val metadata = MetadataRepository(db, TmdbClient(http.tmdb, { tmdbKey.get() }))
    val playback = PlaybackController(context, http.streams)
    val epg = com.aditv.data.epg.EpgRepository(db, http.providerApi, http.images)

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { http.images })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(imageCacheBytes(context)).build() }
        .crossfade(false)
        .build()

    fun syncNow(): Job = appScope.launch {
        val s = accounts.session.value as? Session.Active ?: return@launch
        if (!sync.isRunning) sync.run(s.account.id, s.adapter)
        backfillVariants()
        refreshEpg()
        enrichInBackground()
    }

    /** Guide : jamais pendant une lecture (Fire TV Stick 1re génération : parsing coûteux). */
    suspend fun refreshEpg(force: Boolean = false) {
        val s = accounts.session.value as? Session.Active ?: return
        if (playback.activeSessions.value > 0 && !force) return
        epg.ensureProviderSource(s.account.id, if (s.isDemo) "Guide démo (fictif)" else "Guide du fournisseur")
        val last = epg.lastRefresh(s.account.id)
        if (force || last == null || System.currentTimeMillis() - last > 11 * 3_600_000L) epg.refresh(s.account.id, s.adapter, s.account.serverTimezone)
    }

    /** Enrichissement TMDB des ajouts récents, après synchronisation, si une clé est configurée. */
    fun enrichInBackground(): Job = appScope.launch {
        val s = accounts.session.value as? Session.Active ?: return@launch
        if (tmdbKey.get() != null && playback.activeSessions.value == 0) { metadata.refreshMissingLogos(s.account.id); metadata.enrichRecent(s.account.id) }
    }

    /** Après la mise à jour 0.3 : calcule les variantes des éléments déjà en cache (par lots, hors interface). */
    suspend fun backfillVariants() {
        val dao = db.catalog()
        while (true) {
            val rows = dao.itemsWithoutVariant(500)
            if (rows.isEmpty()) return
            dao.setVariants(rows.map { it.id to com.aditv.data.variants.VariantParser.parse(it.rawTitle, it.type, it.categoryName, it.year) })
        }
    }

    /**
     * Après un correctif de lecture du catalogue (0.3.1 : `category_ids`), resynchronise une fois
     * pour rattacher les titres déjà en cache à leurs catégories.
     */
    suspend fun resyncIfParserChanged() {
        if (prefs.catalogParseVersion() >= CATALOG_PARSE_VERSION) return
        val s = accounts.session.value as? Session.Active ?: return
        if (!sync.isRunning) sync.run(s.account.id, s.adapter)
        prefs.setCatalogParseVersion(CATALOG_PARSE_VERSION)
        backfillVariants()
    }

    suspend fun syncIfStale(maxAgeMs: Long) {
        val s = accounts.session.value as? Session.Active ?: return
        val states = db.catalog().syncStates(s.account.id)
        val oldest = states.minOfOrNull { it.lastSuccessAt ?: 0L } ?: 0L
        if (states.size < 3 || System.currentTimeMillis() - oldest > maxAgeMs) {
            if (!sync.isRunning) sync.run(s.account.id, s.adapter)
        }
        refreshEpg()
    }
}
