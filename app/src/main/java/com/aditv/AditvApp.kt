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
        com.aditv.core.CrashReporter.install(this)
        // Journaux internes de Media3 : avertissements en debug, coupés en release (aucune URL de flux dans logcat).
        androidx.media3.common.util.Log.setLogLevel(
            if (BuildConfig.DEBUG) androidx.media3.common.util.Log.LOG_LEVEL_WARNING else androidx.media3.common.util.Log.LOG_LEVEL_OFF
        )
        com.aditv.data.profile.Profiles.init(this)
        container = AppContainer(this)
        com.aditv.core.CrashReporter.extraState = {
            container.sync.running.value.values.joinToString(" · ") { "Synchro ${it.type.name} : ${it.stage}, ${it.count} reçus" }.ifEmpty { "Aucune synchro en cours" }
        }
        container.appScope.launch {
            container.accounts.restore()
            // Accueil d'abord : les tâches de fond attendent qu'il soit affiché (processeur de la Stick partagé),
            // sauf premier import (catalogue vide).
            val firstImport = (container.accounts.session.value as? com.aditv.data.Session.Active)
                ?.let { s -> container.db.catalog().syncStates(s.account.id).none { it.lastSuccessAt != null } } ?: true
            if (!firstImport) kotlinx.coroutines.delay(STARTUP_GRACE_MS)
            // Rappels reprogrammés, matchs des équipes suivies et tendances TMDB, sans attendre la synchronisation.
            launch { container.reminders.rescheduleAll(); container.refreshSport(); if (container.tmdbKey.get() != null) container.trending.refreshIfStale() }
            container.backfillVariants(); container.resyncIfParserChanged()
            // Groupe jamais synchronisé ou interrompu : relancé dès l'ouverture, sans attendre 12 h.
            container.syncIfStale(maxAgeMs = java.util.concurrent.TimeUnit.HOURS.toMillis(11))
        }
        // Premier plan : un rappel s'affiche en bandeau ; sinon en notification.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(a: android.app.Activity) { started++; inForeground = started > 0; com.aditv.core.CrashReporter.foreground(true) }
            override fun onActivityStopped(a: android.app.Activity) {
                started = (started - 1).coerceAtLeast(0); inForeground = started > 0
                if (!inForeground) com.aditv.core.CrashReporter.foreground(false)
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        // WorkManager absent (environnement de test) : l'app reste utilisable, actualisation manuelle.
        runCatching { CatalogSyncWorker.schedule(this) }.onFailure { com.aditv.core.AppLog.w("Planification indisponible", it) }
    }

    /** Fire TV Stick 1re génération : on rend la mémoire des images dès que Fire OS la réclame. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        com.aditv.core.CrashReporter.event("Fire OS demande de libérer de la mémoire (niveau $level)")
        if (level >= TRIM_MEMORY_RUNNING_LOW) runCatching { container.imageLoader.memoryCache?.clear() }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.aditv.core.CrashReporter.event("Mémoire système basse")
        runCatching { container.imageLoader.memoryCache?.clear() }
    }

    override fun newImageLoader(context: android.content.Context) = container.imageLoader

    companion object {
        @Volatile var inForeground = false
        /** Délai avant les tâches de fond au démarrage (l'accueil s'affiche d'abord). */
        @Volatile var STARTUP_GRACE_MS = com.aditv.core.DeviceProfile.startupGraceMs
    }
}
