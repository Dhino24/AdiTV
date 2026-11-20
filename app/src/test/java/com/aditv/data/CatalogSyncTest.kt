package com.aditv.data

import com.aditv.data.db.CatalogCard
import com.aditv.data.db.CatalogDao
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.db.CatalogItemSyncUpdate
import com.aditv.data.db.CategoryEntity
import com.aditv.data.db.CategorySyncUpdate
import com.aditv.data.db.SyncStateEntity
import com.aditv.data.provider.AccountStatus
import com.aditv.data.provider.MediaType
import com.aditv.data.provider.ProviderAdapter
import com.aditv.data.provider.ProviderError
import com.aditv.data.provider.ProviderKind
import com.aditv.data.provider.RawCategory
import com.aditv.data.provider.RawItem
import com.aditv.data.sync.CatalogSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSyncTest {
    /** DAO en mémoire reproduisant la sémantique SQL utilisée (insert ignore + update partiel). */
    private class FakeDao : CatalogDao {
        val items = linkedMapOf<String, CatalogItemEntity>()
        val cats = linkedMapOf<String, CategoryEntity>()
        val states = linkedMapOf<String, SyncStateEntity>()
        override suspend fun insertItemsIgnore(items: List<CatalogItemEntity>) { items.forEach { this.items.putIfAbsent(it.id, it) } }
        override suspend fun updateItems(items: List<CatalogItemSyncUpdate>) {
            items.forEach { u -> this.items[u.id]?.let { e -> this.items[u.id] = e.copy(title = u.title, available = u.available, lastSeenSync = u.lastSeenSync, categoryId = u.categoryId) } }
        }
        override suspend fun insertCategoriesIgnore(c: List<CategoryEntity>) { c.forEach { cats.putIfAbsent(it.id, it) } }
        override suspend fun updateCategories(c: List<CategorySyncUpdate>) { c.forEach { u -> cats[u.id]?.let { cats[u.id] = it.copy(name = u.name, present = u.present, lastSeenSync = u.lastSeenSync) } } }
        override suspend fun markMissingItems(accountId: String, type: String, syncId: Long): Int {
            var n = 0
            items.replaceAll { _, e -> if (e.accountId == accountId && e.type == type && e.lastSeenSync != syncId && e.available) { n++; e.copy(available = false) } else e }
            return n
        }
        override suspend fun markMissingCategories(accountId: String, type: String, syncId: Long): Int {
            cats.replaceAll { _, c -> if (c.accountId == accountId && c.type == type && c.lastSeenSync != syncId) c.copy(present = false) else c }; return 0
        }
        override suspend fun upsertSyncState(s: SyncStateEntity) { states["${s.accountId}|${s.type}"] = s }
        override fun observeSyncStates(accountId: String): Flow<List<SyncStateEntity>> = emptyFlow()
        override suspend fun syncStates(accountId: String) = states.values.filter { it.accountId == accountId }
        override suspend fun deleteItems(accountId: String) {}
        override suspend fun deleteCategories(accountId: String) {}
        override suspend fun deleteSyncStates(accountId: String) {}
        override fun categories(accountId: String, type: String): Flow<List<CategoryEntity>> = emptyFlow()
        override fun cardsInCategory(accountId: String, type: String, categoryId: String): Flow<List<CatalogCard>> = emptyFlow()
        override fun recentlyAdded(accountId: String, type: String, limit: Int): Flow<List<CatalogCard>> = emptyFlow()
        override fun firstItems(accountId: String, type: String, limit: Int): Flow<List<CatalogCard>> = emptyFlow()
        override suspend fun search(accountId: String, key: String, limit: Int) = emptyList<CatalogCard>()
        override fun observeItem(id: String): Flow<CatalogItemEntity?> = emptyFlow()
        override suspend fun categoryName(id: String): String? = null
        override fun count(accountId: String, type: String): Flow<Int> = emptyFlow()
        override suspend fun variantSiblings(accountId: String, type: String, variantKey: String) = emptyList<CatalogItemEntity>()
        override suspend fun itemsWithoutVariant(limit: Int) = emptyList<com.aditv.data.db.VariantBackfillRow>()
        override suspend fun setVariant(id: String, key: String, lang: String?, quality: Int) {}
        override suspend fun allChannels(accountId: String) = emptyList<CatalogCard>()
        override fun allCards(accountId: String, type: String): Flow<List<CatalogCard>> = emptyFlow()
    }

    private class FakeAdapter(var movies: List<String>, var failMovies: ProviderError? = null) : ProviderAdapter {
        override val kind = ProviderKind.DEMO
        override suspend fun authenticate() = AccountStatus(null, null, null, null, null, null, "fake")
        override suspend fun categories(type: MediaType) = listOf(RawCategory("c1", "FR| Films", 0))
        override suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit) {
            if (type != MediaType.MOVIE) return
            failMovies?.let { onBatch(listOf(raw(movies.first()))); throw it }
            movies.chunked(batchSize).forEach { onBatch(it.map(::raw)) }
        }
        override suspend fun seriesEpisodes(seriesProviderId: String) = emptyList<com.aditv.data.provider.RawEpisode>()
        override fun streamUrl(target: com.aditv.data.provider.StreamTarget): String? = null
        override fun xmltvUrl(): String? = null
        override suspend fun shortEpg(streamProviderId: String) = emptyList<com.aditv.data.provider.RawShortProgramme>()
        private fun raw(id: String) = RawItem(id, MediaType.MOVIE, "FR| Titre $id", null, "c1", null, null, null, null, null, null, null, null, null, null)
    }

    private var t = 1000L
    private fun sync(dao: FakeDao) = CatalogSync(dao) { t++ }

    @Test fun marksMissingItemsUnavailableButKeepsThem() = runTest {
        val dao = FakeDao(); val adapter = FakeAdapter(listOf("1", "2", "3"))
        sync(dao).run("acc", adapter)
        val firstSeen = dao.items.getValue("acc|MOVIE|1").firstSeenAt
        adapter.movies = listOf("1", "3")
        sync(dao).run("acc", adapter)
        assertEquals(3, dao.items.size)
        assertFalse(dao.items.getValue("acc|MOVIE|2").available)
        assertTrue(dao.items.getValue("acc|MOVIE|1").available)
        assertEquals("firstSeenAt préservé", firstSeen, dao.items.getValue("acc|MOVIE|1").firstSeenAt)
        assertEquals("Titre 1", dao.items.getValue("acc|MOVIE|1").title)
    }

    @Test fun failedImportDoesNotMarkAnythingMissing() = runTest {
        val dao = FakeDao(); val adapter = FakeAdapter(listOf("1", "2"))
        sync(dao).run("acc", adapter)
        adapter.failMovies = ProviderError.HttpStatus(502)
        val done = sync(dao).run("acc", adapter)
        assertTrue(dao.items.values.all { it.available })
        assertTrue(done.errors.containsKey(MediaType.MOVIE))
        val st = dao.states.getValue("acc|MOVIE")
        assertEquals(2, st.itemCount); assertTrue(st.lastError!!.contains("502"))
    }

    @Test fun authErrorStopsRemainingTypes() = runTest {
        val dao = FakeDao()
        val adapter = object : ProviderAdapter {
            override val kind = ProviderKind.DEMO
            var calls = 0
            override suspend fun authenticate() = error("unused")
            override suspend fun categories(type: MediaType): List<RawCategory> { calls++; throw ProviderError.InvalidCredentials() }
            override suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit) {}
            override suspend fun seriesEpisodes(seriesProviderId: String) = emptyList<com.aditv.data.provider.RawEpisode>()
            override fun streamUrl(target: com.aditv.data.provider.StreamTarget): String? = null
            override fun xmltvUrl(): String? = null
            override suspend fun shortEpg(streamProviderId: String) = emptyList<com.aditv.data.provider.RawShortProgramme>()
        }
        sync(dao).run("acc", adapter)
        assertEquals(1, adapter.calls)
    }

    /** Serveur qui refuse la liste complète (vide ou erreur) mais sert chaque catégorie. */
    private class PerCategoryAdapter(val fullFails: Boolean, val failingCategory: String? = null) : ProviderAdapter {
        override val kind = ProviderKind.DEMO
        override suspend fun authenticate() = AccountStatus(null, null, null, null, null, null, "fake")
        override suspend fun categories(type: MediaType) = listOf(RawCategory("c1", "Action", 0), RawCategory("c2", "Drame", 1))
        override suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit) {
            if (fullFails && type == MediaType.MOVIE) throw ProviderError.Timeout(java.net.SocketTimeoutException())
        }
        override suspend fun streamItemsInCategory(type: MediaType, categoryProviderId: String, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit) {
            if (type != MediaType.MOVIE) return
            if (categoryProviderId == failingCategory) throw ProviderError.HttpStatus(500)
            onBatch(listOf(RawItem("$categoryProviderId-1", MediaType.MOVIE, "FR| Film $categoryProviderId", null, null, null, null, null, null, null, null, null, null, null, null)))
        }
        override suspend fun seriesEpisodes(seriesProviderId: String) = emptyList<com.aditv.data.provider.RawEpisode>()
        override fun streamUrl(target: com.aditv.data.provider.StreamTarget): String? = null
        override fun xmltvUrl(): String? = null
        override suspend fun shortEpg(streamProviderId: String) = emptyList<com.aditv.data.provider.RawShortProgramme>()
    }

    @Test fun emptyFullListFallsBackToCategories() = runTest {
        val dao = FakeDao()
        val done = sync(dao).run("acc", PerCategoryAdapter(fullFails = false))
        assertEquals(2, done.counts[MediaType.MOVIE])
        assertEquals("acc|MOVIE|cat:c1", dao.items.getValue("acc|MOVIE|c1-1").categoryId)
        val st = dao.states.getValue("acc|MOVIE")
        assertEquals(2, st.categoryCount); assertTrue(st.detail!!.startsWith("par catégorie"))
    }

    @Test fun failingFullListFallsBackAndPartialFailureMarksNothingMissing() = runTest {
        val dao = FakeDao()
        sync(dao).run("acc", PerCategoryAdapter(fullFails = true))
        assertEquals(2, dao.items.size)
        // Deuxième import : la catégorie c2 échoue → son film ne doit pas être masqué.
        sync(dao).run("acc", PerCategoryAdapter(fullFails = true, failingCategory = "c2"))
        assertTrue(dao.items.getValue("acc|MOVIE|c2-1").available)
        assertTrue(dao.states.getValue("acc|MOVIE").detail!!.contains("1 en échec"))
    }

    /** Chaînes interminables (bouquet énorme) : ne doivent jamais bloquer films et séries. */
    private class SlowLiveAdapter(val liveGate: kotlinx.coroutines.CompletableDeferred<Unit>) : ProviderAdapter {
        override val kind = ProviderKind.DEMO
        override suspend fun authenticate() = AccountStatus(null, null, null, null, null, null, "fake")
        override suspend fun categories(type: MediaType) = listOf(RawCategory("c", "Cat", 0))
        override suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit) {
            if (type == MediaType.LIVE) { onBatch(listOf(raw("l1", type))); liveGate.await() }
            else onBatch(listOf(raw("${type.name.lowercase()}1", type)))
        }
        private fun raw(id: String, t: MediaType) = RawItem(id, t, "Titre $id", null, "c", null, null, null, null, null, null, null, null, null, null)
        override suspend fun seriesEpisodes(seriesProviderId: String) = emptyList<com.aditv.data.provider.RawEpisode>()
        override fun streamUrl(target: com.aditv.data.provider.StreamTarget): String? = null
        override fun xmltvUrl(): String? = null
        override suspend fun shortEpg(streamProviderId: String) = emptyList<com.aditv.data.provider.RawShortProgramme>()
    }

    @Test fun moviesAndSeriesSyncWhileLiveIsStillRunning() = runTest {
        val dao = FakeDao(); val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val sync = sync(dao); val adapter = SlowLiveAdapter(gate)
        val live = launch { sync.run("acc", adapter, listOf(MediaType.LIVE)) }
        testScheduler.advanceUntilIdle()
        assertTrue(sync.isRunning(MediaType.LIVE))
        // Films/séries indépendants : terminés alors que les chaînes tournent encore.
        val done = sync.run("acc", adapter, listOf(MediaType.MOVIE, MediaType.SERIES))
        assertEquals(1, done.counts[MediaType.MOVIE]); assertEquals(1, done.counts[MediaType.SERIES])
        assertTrue(dao.states.getValue("acc|MOVIE").lastSuccessAt != null)
        // Un second import des chaînes est ignoré tant que le premier tourne.
        assertTrue(sync.run("acc", adapter, listOf(MediaType.LIVE)).counts.isEmpty())
        gate.complete(Unit); live.join()
        assertTrue(!sync.isRunning)
    }

    @Test fun interruptedImportIsReportedNotSilent() = runTest {
        val dao = FakeDao(); val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val sync = sync(dao)
        val live = launch { sync.run("acc", SlowLiveAdapter(gate), listOf(MediaType.LIVE)) }
        testScheduler.advanceUntilIdle()
        live.cancel(); live.join()
        val st = dao.states.getValue("acc|LIVE")
        assertEquals(CatalogSync.INTERRUPTED, st.detail)
        assertEquals(null, st.lastSuccessAt)
        assertTrue("verrou libéré après annulation", !sync.isRunning(MediaType.LIVE))
    }

    @Test fun idsAreScopedByAccount() = runTest {
        val dao = FakeDao(); val adapter = FakeAdapter(listOf("1"))
        sync(dao).run("a", adapter); sync(dao).run("b", adapter)
        assertTrue(dao.items.containsKey("a|MOVIE|1") && dao.items.containsKey("b|MOVIE|1"))
    }
}
