package com.aditv.data

import com.aditv.data.football.FootballClient
import com.aditv.data.football.FootballError
import com.aditv.data.football.FootballRepository
import com.aditv.ui.foot.Broadcasts
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FootballTest {
    private val server = MockWebServer().apply { start() }
    @After fun tearDown() = server.close()
    private fun client() = FootballClient(OkHttpClient(), { "cle" }, server.url("/").toString().trimEnd('/'))

    @Test fun parsesFixturesAndFiltersCompetitions() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"errors":[],"response":[
          {"fixture":{"id":1,"timestamp":1795000000,"status":{"short":"2H","elapsed":67}},"league":{"id":2,"name":"UEFA Champions League","country":"World","round":"League Stage - 5"},
           "teams":{"home":{"id":85,"name":"Paris Saint Germain","logo":"https://x/85.png"},"away":{"id":157,"name":"Bayern München","logo":null}},"goals":{"home":2,"away":1}},
          {"fixture":{"id":2,"timestamp":1795003600,"status":{"short":"NS","elapsed":null}},"league":{"id":999,"name":"Liga obscure","country":"Nowhere"},
           "teams":{"home":{"id":1,"name":"A"},"away":{"id":2,"name":"B"}},"goals":{"home":null,"away":null}}]}""").build())
        val list = client().fixtures("2026-11-23")
        assertEquals(2, list.size)
        val f = list[0]
        assertTrue(f.live); assertEquals(67, f.elapsed); assertEquals(2, f.homeGoals); assertEquals(1_795_000_000_000L, f.kickoff)
        assertTrue(FootballRepository.keep(f)); assertTrue(FootballRepository.isFollowed(f))
        assertFalse("compétition non suivie écartée", FootballRepository.keep(list[1]))
        assertTrue(server.takeRequest().headers["x-apisports-key"] == "cle")
    }

    @Test fun reportsPlanErrors() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"errors":{"plan":"Free plans do not have access to this season"},"response":[]}""").build())
        try { client().fixtures("2026-11-23"); fail() } catch (e: FootballError) { assertTrue(e.message!!.contains("Free plans")) }
    }

    @Test fun findsTeamsInGuideTitles() {
        assertTrue(Broadcasts.mentions("Ligue des champions : PSG - Bayern", "Paris Saint Germain"))
        assertTrue(Broadcasts.mentions("Ligue des champions : PSG - Bayern", "Bayern München"))
        assertTrue(Broadcasts.mentions("Premier League : Brentford / Everton", "Everton"))
        assertTrue(Broadcasts.mentions("Liga : FC Barcelone - Séville", "Barcelona"))
        assertFalse(Broadcasts.mentions("Liga : Espanyol - Séville", "Barcelona"))
        assertFalse(Broadcasts.mentions("Journal de 20 heures", "Everton"))
    }
}
