package com.aditv

import android.app.Application
import coil3.SingletonImageLoader
import com.aditv.data.sync.CatalogSyncWorker
import com.aditv.di.AppContainer
import kotlinx.coroutines.launch

class AditvApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.accounts.restore() }
        // WorkManager absent (environnement de test) : l'app reste utilisable, actualisation manuelle.
        runCatching { CatalogSyncWorker.schedule(this) }.onFailure { com.aditv.core.AppLog.w("Planification indisponible", it) }
    }

    override fun newImageLoader(context: android.content.Context) = container.imageLoader
}
