package com.aditv.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aditv.AditvApp
import java.util.concurrent.TimeUnit

/** Actualisation en arrière-plan toutes les 12 h, réseau requis. Ne relance pas une erreur d'accès en boucle. */
class CatalogSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as AditvApp).container
        container.syncIfStale(maxAgeMs = TimeUnit.HOURS.toMillis(11))
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<CatalogSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("catalog-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
