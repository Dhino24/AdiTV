package com.aditv.data

import com.aditv.core.Redactor
import com.aditv.data.library.ProgressPolicy
import com.aditv.data.provider.ProviderCredentials
import com.aditv.data.provider.StreamTarget
import com.aditv.data.provider.XtreamAdapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesAndStreamTest {
    private val adapter = XtreamAdapter(OkHttpClient(), ProviderCredentials("http://p.example:8080".toHttpUrl(), "alice", "s3cret"))
    private fun parse(s: String) = Json.decodeFromString(JsonElement.serializer(), s)

    @Test fun parsesEpisodesKeyedBySeason() {
        val eps = adapter.parseSeriesEpisodes(parse("""{"seasons":[],"episodes":{
            "2":[{"id":"21","episode_num":"1","title":"S2E1","container_extension":"mkv","info":{"duration_secs":"2580","plot":"P"}}],
            "1":[{"id":"12","episode_num":2,"title":"S1E2"},{"id":"11","episode_num":1,"title":"S1E1","info":{"movie_image":"http://img/e.jpg"}}]}}"""))
        assertEquals(listOf("11", "12", "21"), eps.map { it.providerId })
        assertEquals(2580, eps[2].durationSec); assertEquals("mkv", eps[2].containerExt); assertEquals("http://img/e.jpg", eps[0].imageUrl)
    }

    @Test fun parsesEpisodesAsListAndIgnoresJunk() {
        val eps = adapter.parseSeriesEpisodes(parse("""{"episodes":[[{"id":"5","season":3,"episode_num":1,"title":"A"}],{"title":"sans id"}]}"""))
        assertEquals(1, eps.size); assertEquals(3, eps[0].season)
        assertTrue(adapter.parseSeriesEpisodes(parse("""{"episodes":null}""")).isEmpty())
    }

    @Test fun buildsStreamUrlsOnLoginHostAndRedactsThem() {
        val movie = adapter.streamUrl(StreamTarget.Movie("123", "mkv"))
        assertEquals("http://p.example:8080/movie/alice/s3cret/123.mkv", movie)
        assertEquals("http://p.example:8080/series/alice/s3cret/9.mp4", adapter.streamUrl(StreamTarget.Episode("9", null)))
        assertEquals("http://p.example:8080/live/alice/s3cret/7.ts", adapter.streamUrl(StreamTarget.Live("7")))
        adapter.liveExtension = "m3u8"
        assertEquals("http://p.example:8080/live/alice/s3cret/7.m3u8", adapter.streamUrl(StreamTarget.Live("7")))
        val logged = Redactor.redact("Erreur sur $movie")
        assertFalse(logged.contains("alice") || logged.contains("s3cret"))
    }

    @Test fun progressPolicy() {
        val h2 = 2 * 3_600_000L
        assertNull("aperçu", ProgressPolicy.resumePosition(20_000, h2, false))
        assertEquals(595_000L, ProgressPolicy.resumePosition(600_000, h2, false))
        assertTrue("générique", ProgressPolicy.isCompleted(h2 - 120_000, h2))
        assertNull(ProgressPolicy.resumePosition(h2 - 120_000, h2, false))
        assertFalse("court contenu non terminé à 2 min de la fin", ProgressPolicy.isCompleted(300_000, 420_000))
        assertFalse("durée inconnue", ProgressPolicy.isCompleted(10_000, 0))
    }
}

class ShortEpgAndCatchupTest {
    private val adapter = XtreamAdapter(OkHttpClient(), ProviderCredentials("http://p.example:8080".toHttpUrl(), "alice", "s3cret"))

    @Test fun decodesBase64ShortEpg() {
        val t = java.util.Base64.getEncoder().encodeToString("Journal de 20 h".toByteArray())
        val list = adapter.parseShortEpg(Json.decodeFromString(JsonElement.serializer(),
            """{"epg_listings":[{"title":"$t","description":"","start_timestamp":"1795000000","stop_timestamp":"1795003600"}]}"""))
        assertEquals("Journal de 20 h", list.single().title)
        assertEquals(1_795_000_000_000L, list.single().startUtc)
    }

    @Test fun catchupUrlUsesServerLocalTime() {
        val start = java.time.Instant.parse("2026-11-19T19:00:00Z").toEpochMilli()
        val url = adapter.streamUrl(StreamTarget.Catchup("42", start, 60, "Europe/Paris"))
        assertEquals("http://p.example:8080/timeshift/alice/s3cret/60/2026-11-19:20-00/42.ts", url)
    }
}
