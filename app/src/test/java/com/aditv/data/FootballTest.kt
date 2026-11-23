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

    private val finishedDay = """{"errors":[],"response":[
      {"fixture":{"id":7,"timestamp":1795000000,"status":{"short":"FT","elapsed":90}},"league":{"id":61,"name":"Ligue 1","country":"France"},
       "teams":{"home":{"id":85,"name":"Paris Saint Germain"},"away":{"id":80,"name":"Lyon"}},"goals":{"home":2,"away":0}}]}"""

    /** Quota de 100 requêtes/jour : un jour passé terminé et ses buts ne sont demandés qu'une fois, même après redémarrage. */
    @Test fun savesRequestsWithDiskCacheAndBatchedGoals() = runTest {
        val dir = kotlin.io.path.createTempDirectory("foot").toFile()
        server.enqueue(MockResponse.Builder().body(finishedDay).addHeader("x-ratelimit-requests-remaining", "97").addHeader("x-ratelimit-requests-limit", "100").build())
        server.enqueue(MockResponse.Builder().body("""{"errors":[],"response":[{"fixture":{"id":7},"events":[
            {"time":{"elapsed":12},"team":{"id":85},"player":{"name":"Dembélé"},"type":"Goal","detail":"Normal Goal"},
            {"time":{"elapsed":30},"team":{"id":80},"player":{"name":"X"},"type":"Card","detail":"Yellow Card"},
            {"time":{"elapsed":77},"team":{"id":85},"player":{"name":"Doué"},"type":"Goal","detail":"Penalty"}],
            "lineups":[{"team":{"id":85,"name":"Paris Saint Germain"},"formation":"4-3-3","coach":{"name":"L. Enrique"},
              "startXI":[{"player":{"number":1,"name":"Chevalier","pos":"G"}},{"player":{"number":10,"name":"Dembélé","pos":"F"}}],"substitutes":[{"player":{"number":14,"name":"Doué","pos":"F"}}]},
             {"team":{"id":80,"name":"Lyon"},"formation":"4-2-3-1","coach":{"name":"P. Fonseca"},"startXI":[{"player":{"number":1,"name":"Perri","pos":"G"}}],"substitutes":[]}]}]}""").build())
        val repo = FootballRepository(client(), dir)
        val f = repo.fixtures(-1).single()
        assertEquals(97, repo.remaining); assertEquals(100, repo.dailyLimit)
        assertEquals(listOf("Dembélé", "Doué"), repo.goals(f, listOf(f)).map { it.player })
        assertTrue(server.takeRequest().url.toString().contains("/fixtures?date="))
        assertTrue(server.takeRequest().url.toString().contains("/fixtures?ids=7"))
        // « Redémarrage » : nouveau dépôt, même dossier → aucune requête de plus.
        val again = FootballRepository(client(), dir)
        val f2 = again.fixtures(-1).single()
        val d = again.details(f2, listOf(f2))
        assertEquals(2, d.goals.size)
        // Compositions arrivées dans la même requête que les buts, gardées sur disque.
        assertEquals(listOf("4-3-3", "4-2-3-1"), d.lineups.map { it.formation })
        assertEquals("Dembélé", d.lineups[0].startXI[1].name); assertEquals("L. Enrique", d.lineups[0].coach)
        assertEquals(2, server.requestCount)
        dir.deleteRecursively()
    }

    @Test fun parsesStandingsWithGroups() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"errors":[],"response":[{"league":{"id":61,"season":2026,"standings":[[
          {"rank":1,"team":{"id":85,"name":"Paris Saint Germain","logo":"x"},"points":31,"goalsDiff":18,"group":"Ligue 1","form":"WWDWW","description":"Promotion - Champions League (League phase: )",
           "all":{"played":13,"win":10,"draw":1,"lose":2}},
          {"rank":18,"team":{"id":99,"name":"Metz"},"points":7,"goalsDiff":-15,"group":"Ligue 1","form":"LLDLL","description":"Relegation - Ligue 2","all":{"played":13,"win":1,"draw":4,"lose":8}}]]}}]}""").build())
        val rows = client().parseStandings(client().standingsRaw(61, 2026))
        assertEquals(2, rows.size)
        assertEquals(31, rows[0].points); assertEquals(10, rows[0].win); assertEquals("WWDWW", rows[0].form)
        assertEquals(-15, rows[1].goalsDiff); assertTrue(rows[1].description!!.contains("Relegation"))
        assertTrue(server.takeRequest().url.toString().contains("/standings?league=61&season=2026"))
    }
}
