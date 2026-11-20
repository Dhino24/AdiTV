package com.aditv

import android.app.Application
import coil3.SingletonImageLoader
import com.aditv.data.sync.CatalogSyncWorker
import com.aditv.di.AppContainer
import kotlinx.coroutines.launch

class AditvApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Journaux internes de Media3 : avertissements en debug, coupés en release (aucune URL de flux dans logcat).
        androidx.media3.common.util.Log.setLogLevel(
            if (BuildConfig.DEBUG) androidx.media3.common.util.Log.LOG_LEVEL_WARNING else androidx.media3.common.util.Log.LOG_LEVEL_OFF
        )
        container = AppContainer(this)
        container.appScope.launch {
            container.accounts.restore(); container.backfillVariants(); container.resyncIfParserChanged()
            // Groupe jamais synchronisé ou interrompu : relancé dès l'ouverture, sans attendre 12 h.
            container.syncIfStale(maxAgeMs = java.util.concurrent.TimeUnit.HOURS.toMillis(11))
        }
        // WorkManager absent (environnement de test) : l'app reste utilisable, actualisation manuelle.
        runCatching { CatalogSyncWorker.schedule(this) }.onFailure { com.aditv.core.AppLog.w("Planification indisponible", it) }
    }

    override fun newImageLoader(context: android.content.Context) = container.imageLoader
}
