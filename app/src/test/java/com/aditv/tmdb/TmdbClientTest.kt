package com.aditv.tmdb

import com.aditv.data.tmdb.TmdbClient
import com.aditv.data.tmdb.TmdbError
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

class TmdbClientTest {
    private val server = MockWebServer()
    private var key: String? = "abc123v3key"
    private lateinit var client: TmdbClient

    @Before fun setUp() { server.start(); client = TmdbClient(OkHttpClient(), { key }, server.url("/3/")) }
    @After fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200, headers: Map<String, String> = emptyMap()) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").apply { headers.forEach { (k, v) -> addHeader(k, v) } }.body(body).build()
    )

    @Test fun v3KeyGoesInQueryV4TokenInBearerHeader() = runTest {
        json("""{"results":[]}"""); json("""{"results":[]}""")
        client.search("movie", "Dune", null)
        val r1 = server.takeRequest()
        assertEquals("abc123v3key", r1.url.queryParameter("api_key")); assertNull(r1.headers["Authorization"])
        key = "eyJhbGciOiJIUzI1NiJ9.token"
        client.search("movie", "Dune", null)
        val r2 = server.takeRequest()
        assertNull(r2.url.queryParameter("api_key")); assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.token", r2.headers["Authorization"])
    }

    @Test fun searchParsesAndRetriesWithoutYear() = runTest {
        json("""{"results":[]}""")
        json("""{"results":[{"id":438631,"title":"Dune","original_title":"Dune","release_date":"2021-09-15","popularity":95.2,"poster_path":"/p.jpg"},{"id":2,"title":"Sans date"}]}""")
        val r = client.search("movie", "Dune", 2020)
        assertEquals(2, r.size); assertEquals(2021, r[0].year); assertEquals("/p.jpg", r[0].posterPath); assertNull(r[1].year)
        assertEquals("2020", server.takeRequest().url.queryParameter("primary_release_year"))
        assertNull(server.takeRequest().url.queryParameter("primary_release_year"))
    }

    @Test fun detailsFallBackToEnglishOverview() = runTest {
        json("""{"id":11,"title":"Titre FR","original_title":"Original","overview":"","release_date":"2020-01-02","runtime":128,
                 "genres":[{"name":"Thriller"}],"vote_average":7.4,"vote_count":1203,
                 "credits":{"cast":[{"name":"Actrice","character":"Rôle"}],"crew":[{"name":"Réal","job":"Director"},{"name":"X","job":"Editor"}]},
                 "recommendations":{"results":[{"id":5},{"id":6}]},"similar":{"results":[{"id":6},{"id":7}]}}""")
        json("""{"id":11,"overview":"English overview"}""")
        val d = client.details("movie", 11)
        assertEquals("English overview", d.overview); assertEquals("en-US", d.overviewLanguage)
        assertEquals(listOf("Réal"), d.crew); assertEquals("Actrice" to "Rôle", d.cast.first())
        assertEquals(listOf(5, 6, 7), d.relatedIds); assertEquals(128, d.runtimeMin); assertEquals(1203, d.voteCount)
        assertEquals("fr-FR", server.takeRequest().url.queryParameter("language"))
    }

    @Test fun invalidKeyAndMissingKeyAreReported() = runTest {
        json("""{"status_code":7}""", code = 401)
        try { client.search("tv", "x", null); fail() } catch (e: TmdbError.InvalidKey) {}
        key = null
        try { client.search("tv", "x", null); fail() } catch (e: TmdbError.NoKey) {}
    }

    @Test fun rateLimitIsRetriedThenGivesUp() = runTest {
        json("{}", code = 429, headers = mapOf("Retry-After" to "1"))
        json("""{"results":[{"id":1,"name":"Série","first_air_date":"2019-03-01"}]}""")
        assertEquals("Série", client.search("tv", "s", null).single().title)
        repeat(3) { json("{}", code = 503) }
        try { client.search("tv", "s", null); fail() } catch (e: TmdbError.Server) { assertTrue(e.message!!.contains("503")) }
    }
}
