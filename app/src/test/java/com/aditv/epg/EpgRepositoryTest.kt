package com.aditv.epg

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.epg.EpgRepository
import com.aditv.data.provider.DemoAdapter
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class EpgRepositoryTest {
    private lateinit var db: AditvDatabase
    private val server = MockWebServer()
    private val now = 1_795_000_000_000L // horloge fixe
    private lateinit var repo: EpgRepository

    private fun channel(id: String, epg: String?) = CatalogItemEntity(
        "a|LIVE|$id", "a", "LIVE", id, id, id, id, null, null, null, null, null, null, null, epg, null, null, null, null, true, 1, 1,
    )

    @Before fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AditvDatabase::class.java).allowMainThreadQueries().build()
        repo = EpgRepository(db, OkHttpClient(), OkHttpClient()) { now }
    }
    @After fun tearDown() { db.close(); server.close() }

    private fun t(offsetMin: Int) = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneOffset.UTC)
        .format(java.time.Instant.ofEpochMilli(now + offsetMin * 60_000L))

    @Test fun providerFirstOneSourcePerChannelOffsetsAndManualMapping() = runBlocking {
        db.catalog().insertItemsIgnore(listOf(channel("1", "aditv.demo"), channel("2", "ext.only"), channel("3", null)))
        repo.ensureProviderSource("a", "Démo")
        server.enqueue(MockResponse.Builder().body("""<tv><channel id="aditv.demo"><display-name>ADITV Démo bis</display-name></channel>
            <programme channel="aditv.demo" start="${t(-10)}" stop="${t(20)}"><title>Ne doit pas se mélanger</title></programme>
            <programme channel="ext.only" start="${t(-10)}" stop="${t(50)}"><title>Externe</title></programme></tv>""").build())
        repo.addUrlSource("a", server.url("/guide.xml").toString())

        repo.refresh("a", DemoAdapter(), "Europe/Paris")
        val progs = repo.programmesFor("a", listOf("a|LIVE|1" to "aditv.demo", "a|LIVE|2" to "ext.only", "a|LIVE|3" to null), now, now + 3_600_000)
        assertTrue("fournisseur prioritaire", progs.getValue("a|LIVE|1").none { it.title == "Ne doit pas se mélanger" })
        assertTrue(progs.getValue("a|LIVE|1").isNotEmpty())
        assertEquals("Externe", progs.getValue("a|LIVE|2").single().title)
        assertNull("sans ID : rien d'inventé", progs["a|LIVE|3"])

        val cov = repo.coverage("a")
        assertEquals(3, cov.channels); assertEquals(2, cov.withGuideId); assertEquals(2, cov.covered)

        // Association manuelle + décalage de chaîne.
        val ch3 = channel("3", null)
        repo.setManualMapping(ch3, "ext.only")
        repo.setChannelOffset(ch3, 60)
        val shifted = repo.programmesFor("a", listOf("a|LIVE|3" to null), now - 3_600_000, now + 6 * 3_600_000).getValue("a|LIVE|3").single()
        assertEquals(now - 10 * 60_000 + 3_600_000, shifted.startUtc)
        repo.setManualMapping(ch3, null, disabled = true)
        assertNull(repo.programmesFor("a", listOf("a|LIVE|3" to null), now, now + 3_600_000)["a|LIVE|3"])
    }

    @Test fun failedRefreshKeepsPreviousGuide() = runBlocking {
        db.catalog().insertItemsIgnore(listOf(channel("2", "ext.only")))
        server.enqueue(MockResponse.Builder().body("""<tv><programme channel="ext.only" start="${t(-10)}" stop="${t(50)}"><title>Externe</title></programme></tv>""").build())
        server.enqueue(MockResponse.Builder().code(500).body("x").build())
        repo.addUrlSource("a", server.url("/g.xml").toString())
        repo.refresh("a", DemoAdapter(), null)
        repo.refresh("a", DemoAdapter(), null)
        assertEquals(1, repo.programmesFor("a", listOf("a|LIVE|2" to "ext.only"), now, now + 3_600_000).getValue("a|LIVE|2").size)
        assertTrue(db.live().sources("a").single().lastError!!.contains("500"))
    }
}
