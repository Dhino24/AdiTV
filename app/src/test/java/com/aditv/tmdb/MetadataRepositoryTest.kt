package com.aditv.tmdb

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.tmdb.MetadataRepository
import com.aditv.data.tmdb.TmdbClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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
class MetadataRepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: AditvDatabase
    private lateinit var repo: MetadataRepository

    private fun details(id: Int, title: String, date: String) =
        """{"id":$id,"title":"$title","original_title":"$title","overview":"Résumé $title","release_date":"$date","genres":[],"vote_average":7.0,"vote_count":10,"credits":{"cast":[],"crew":[]},"recommendations":{"results":[]},"similar":{"results":[]}}"""

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val body = when {
                    path.endsWith("/search/movie") && request.url.queryParameter("query") == "Dune" ->
                        """{"results":[{"id":438631,"title":"Dune","release_date":"2021-09-15"},{"id":841,"title":"Dune","release_date":"1984-12-14"}]}"""
                    path.endsWith("/search/movie") -> """{"results":[{"id":77338,"title":"Intouchables","release_date":"2011-11-02"}]}"""
                    path.endsWith("/movie/77338") -> details(77338, "Intouchables", "2011-11-02")
                    path.endsWith("/movie/841") -> details(841, "Dune", "1984-12-14")
                    path.endsWith("/movie/999") -> return MockResponse.Builder().code(404).body("{}").build()
                    else -> return MockResponse.Builder().code(404).body("{}").build()
                }
                return MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()
            }
        }
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AditvDatabase::class.java).allowMainThreadQueries().build()
        repo = MetadataRepository(db, TmdbClient(OkHttpClient(), { "k" }, server.url("/3/")))
    }

    @After fun tearDown() { db.close(); server.close() }

    private fun item(id: String, title: String, year: Int?, tmdb: String? = null) = CatalogItemEntity(
        id, "a", "MOVIE", id, title, title, title.lowercase(), year, null, null, tmdb, null, null, null, null, null, null, null, null, true, 1, 1,
    )

    @Test fun automaticAmbiguousAndManualLock() = runBlocking {
        db.catalog().insertItemsIgnore(listOf(item("a|MOVIE|1", "Intouchables", 2011), item("a|MOVIE|2", "Dune", null), item("a|MOVIE|3", "Intouchables", 2011, tmdb = "999")))

        assertNull(repo.ensure("a|MOVIE|1"))
        val m1 = db.metadata().match("a|MOVIE|1")!!
        assertEquals("confirmed", m1.status); assertEquals(77338, m1.tmdbId)
        assertEquals("Résumé Intouchables", db.metadata().metadata("movie", 77338)!!.overview)

        // Remake sans année : ambigu, candidats proposés, aucun choix arbitraire.
        repo.ensure("a|MOVIE|2")
        val m2 = db.metadata().match("a|MOVIE|2")!!
        assertEquals("ambiguous", m2.status); assertNull(m2.tmdbId)
        assertEquals(2, repo.candidatesOf(m2).size)

        // Correction manuelle : verrouillée et conservée même si on force un nouveau matching.
        repo.chooseManually("a|MOVIE|2", "movie", 841)
        repo.ensure("a|MOVIE|2", force = true)
        val locked = db.metadata().match("a|MOVIE|2")!!
        assertEquals(841, locked.tmdbId); assertTrue(locked.locked); assertEquals("manual", locked.origin)

        // ID fourni inexistant sur TMDB : ignoré, recherche classique.
        repo.ensure("a|MOVIE|3")
        assertEquals(77338, db.metadata().match("a|MOVIE|3")!!.tmdbId)
    }
}
