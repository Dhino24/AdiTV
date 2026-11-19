package com.aditv.data.sync

import com.aditv.core.AppLog
import com.aditv.data.db.CatalogDao
import com.aditv.data.db.SyncStateEntity
import com.aditv.data.provider.MediaType
import com.aditv.data.provider.ProviderAdapter
import com.aditv.data.provider.ProviderError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncProgress {
    data object Idle : SyncProgress
    data class Running(val type: MediaType, val stage: String, val count: Int) : SyncProgress
    data class Done(val at: Long, val counts: Map<MediaType, Int>, val errors: Map<MediaType, String>) : SyncProgress
}

/**
 * Import progressif : catégories puis éléments, type par type, par lots transactionnels.
 * Le catalogue précédent reste consultable pendant l'import. Rien n'est supprimé :
 * les éléments absents d'un import réussi passent « indisponibles ».
 * Un échec sur un type n'efface rien et n'empêche pas les autres types.
 */
class CatalogSync(private val dao: CatalogDao, private val clock: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()
    private val _progress = MutableStateFlow<SyncProgress>(SyncProgress.Idle)
    val progress: StateFlow<SyncProgress> = _progress

    val isRunning get() = mutex.isLocked

    suspend fun run(accountId: String, adapter: ProviderAdapter, types: List<MediaType> = MediaType.entries): SyncProgress.Done =
        mutex.withLock {
            val counts = mutableMapOf<MediaType, Int>()
            val errors = mutableMapOf<MediaType, String>()
            for (type in types) {
                val syncId = clock()
                val prev = dao.syncStates(accountId).firstOrNull { it.type == type.name }
                try {
                    _progress.value = SyncProgress.Running(type, "Catégories", 0)
                    val cats = adapter.categories(type).map { CatalogMapping.toCategory(accountId, type, it, syncId) }
                    dao.upsertCategories(cats, cats.map(CatalogMapping::toCategoryUpdate))

                    var n = 0
                    adapter.streamItems(type, BATCH) { batch ->
                        val now = clock()
                        val entities = batch.map { CatalogMapping.toEntity(accountId, it, syncId, now) }
                        dao.upsertItems(entities, entities.map(CatalogMapping::toUpdate))
                        n += batch.size
                        _progress.value = SyncProgress.Running(type, "Éléments", n)
                    }
                    // Uniquement après un import complet : sinon on marquerait à tort des titres absents.
                    dao.markMissingItems(accountId, type.name, syncId)
                    dao.markMissingCategories(accountId, type.name, syncId)
                    counts[type] = n
                    dao.upsertSyncState(SyncStateEntity(accountId, type.name, clock(), clock(), n, null))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = (e as? ProviderError)?.message ?: "Erreur inattendue (${e.javaClass.simpleName})"
                    AppLog.w("Synchronisation ${type.name} échouée", e)
                    errors[type] = msg
                    dao.upsertSyncState(SyncStateEntity(accountId, type.name, prev?.lastSuccessAt, clock(), prev?.itemCount ?: 0, msg))
                    // Une erreur d'accès ne doit pas être répétée sur les types suivants.
                    if (e is ProviderError.InvalidCredentials || e is ProviderError.AccountInactive) break
                }
            }
            SyncProgress.Done(clock(), counts, errors).also { _progress.value = it }
        }

    companion object { const val BATCH = 500 }
}
