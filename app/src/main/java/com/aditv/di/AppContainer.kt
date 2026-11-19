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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

/** Injection manuelle : pas de framework DI pour un seul module. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = HttpClients()
    val db = AditvDatabase.build(context)
    val prefs = AppPrefs(context)
    val accounts = AccountRepository(db, SecretStore(context), prefs, http)
    val sync = CatalogSync(db.catalog())

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { http.images })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(false)
        .build()

    fun syncNow(): Job = appScope.launch {
        val s = accounts.session.value as? Session.Active ?: return@launch
        if (!sync.isRunning) sync.run(s.account.id, s.adapter)
    }

    suspend fun syncIfStale(maxAgeMs: Long) {
        val s = accounts.session.value as? Session.Active ?: return
        val states = db.catalog().syncStates(s.account.id)
        val oldest = states.minOfOrNull { it.lastSuccessAt ?: 0L } ?: 0L
        if (states.size < 3 || System.currentTimeMillis() - oldest > maxAgeMs) {
            if (!sync.isRunning) sync.run(s.account.id, s.adapter)
        }
    }
}
