package com.aditv.data

import com.aditv.data.provider.MediaType
import com.aditv.data.provider.ProviderCredentials
import com.aditv.data.provider.ProviderError
import com.aditv.data.provider.RawItem
import com.aditv.data.provider.XtreamAdapter
import com.aditv.net.CleartextGate
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class XtreamAdapterTest {
    private val server = MockWebServer()
    private lateinit var adapter: XtreamAdapter

    @Before fun setUp() {
        server.start()
        CleartextGate.setAllowedHosts(setOf(server.hostName))
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor(com.aditv.net.SameHostRedirects())
            .addNetworkInterceptor(CleartextGate(blockCrossHostRedirects = true)).build()
        adapter = XtreamAdapter(client, ProviderCredentials(server.url("/"), "alice", "s3cret"))
    }

    @After fun tearDown() { server.close(); CleartextGate.setAllowedHosts(emptySet()) }

    private fun enqueue(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test fun authenticatesActiveAccount() = runTest {
        enqueue("""{"user_info":{"auth":1,"status":"Active","exp_date":"1798761600","max_connections":"2","active_cons":0,"is_trial":"0"},"server_info":{"timezone":"Europe/Paris"}}""")
        val s = adapter.authenticate()
        assertEquals("Active", s.status)
        assertEquals(1798761600L, s.expiresAtEpochSec)
        assertEquals(2, s.maxConnections)
        assertEquals("Europe/Paris", s.serverTimezone)
        val req = server.takeRequest()
        assertEquals("/player_api.php", req.url.encodedPath)
    }

    @Test fun reportsInvalidCredentialsAndInactive() = runTest {
        enqueue("""{"user_info":{"auth":0}}""")
        try { adapter.authenticate(); fail() } catch (e: ProviderError.InvalidCredentials) {}
        enqueue("""{"user_info":{"auth":1,"status":"Expired","exp_date":null}}""")
        try { adapter.authenticate(); fail() } catch (e: ProviderError.AccountInactive) { assertEquals("Expired", e.status) }
        enqueue("[]")
        try { adapter.authenticate(); fail() } catch (e: ProviderError.InvalidCredentials) {}
    }

    @Test fun mapsHttpAndHtmlErrors() = runTest {
        enqueue("nope", code = 403)
        try { adapter.authenticate(); fail() } catch (e: ProviderError.HttpStatus) { assertEquals(403, e.code) }
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body("<html></html>").build())
        try { adapter.authenticate(); fail() } catch (e: ProviderError.UnexpectedResponse) {}
    }

    @Test fun streamsVodInBatchesWithLenientTypes() = runTest {
        enqueue(
            """[
              {"num":1,"name":"FR| Film A (2020)","stream_id":10,"stream_icon":"http://img/a.jpg","rating":"7.1","tmdb":"603","added":"1700000000","category_id":"5","container_extension":"mkv"},
              {"num":"2","name":"Film B","stream_id":"11","stream_icon":"","rating":0,"tmdb":null,"added":"0","category_id":5},
              {"name":"sans id"},
              {"num":3,"name":"Film C","stream_id":12}
            ]"""
        )
        val batches = mutableListOf<List<RawItem>>()
        adapter.streamItems(MediaType.MOVIE, batchSize = 2) { batches += it }
        assertEquals(listOf(2, 1), batches.map { it.size })
        val a = batches[0][0]
        assertEquals("10", a.providerId); assertEquals("603", a.providerTmdbId); assertEquals(1700000000L, a.addedAtEpochSec)
        assertEquals("http://img/a.jpg", a.imageUrl); assertEquals("5", a.categoryProviderId)
        val b = batches[0][1]
        assertNull(b.imageUrl); assertNull(b.providerRating); assertNull(b.addedAtEpochSec); assertEquals(2, b.position)
    }

    @Test fun readsCategoryIdsListWhenCategoryIdIsMissing() = runTest {
        enqueue("""[{"name":"Film XUI","stream_id":1,"category_id":null,"category_ids":[42,7]},
                    {"name":"Film vide","stream_id":2,"category_id":"","category_ids":["9"]},
                    {"name":"Sans catégorie","stream_id":3}]""")
        val items = mutableListOf<com.aditv.data.provider.RawItem>()
        adapter.streamItems(MediaType.MOVIE, 10) { items += it }
        assertEquals(listOf("42", "9", null), items.map { it.categoryProviderId })
    }

    @Test fun acceptsObjectKeyedListsAndEmptyBodies() = runTest {
        enqueue("""{"1":{"category_id":"1","category_name":"A"},"2":{"category_id":"2","category_name":"B"}}""")
        assertEquals(listOf("A", "B"), adapter.categories(MediaType.LIVE).map { it.name })
        enqueue("")
        assertTrue(adapter.categories(MediaType.LIVE).isEmpty())
    }

    /** Liste de films renvoyée en objet indexé : lue entrée par entrée (accolades et guillemets dans les titres). */
    @Test fun streamsObjectKeyedVodWithoutLoadingItAll() = runTest {
        val body = buildString {
            append("{")
            for (i in 1..3000) {
                if (i > 1) append(",\n")
                append("\"$i\": {\"num\": $i, \"name\": \"Film {$i} \\\"cité\\\" [x]\", \"stream_id\": $i, \"rating\": 7.5, \"tmdb\": null, \"category_ids\": [1, 2], \"adult\": false}")
            }
            append("}")
        }
        enqueue(body)
        val items = mutableListOf<RawItem>()
        adapter.streamItems(MediaType.MOVIE, batchSize = 500) { items += it }
        assertEquals(3000, items.size)
        assertEquals("1", items.first().providerId)
        assertTrue(items.last().rawTitle.contains("\"cité\""))
        assertEquals("1", items.first().categoryProviderId)
    }

    @Test fun refusesCrossHostRedirect() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://other.invalid/player_api.php").build())
        try { adapter.authenticate(); fail() } catch (e: ProviderError.RedirectRefused) {}
    }

    @Test fun followsSameHostRedirect() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/v2/player_api.php?username=alice&password=s3cret").build())
        enqueue("""{"user_info":{"auth":1,"status":"Active"}}""")
        assertEquals("Active", adapter.authenticate().status)
    }

    @Test fun refusesCleartextWithoutConsent() = runTest {
        CleartextGate.setAllowedHosts(emptySet())
        try { adapter.authenticate(); fail() } catch (e: ProviderError.CleartextNotAllowed) {}
    }
}
