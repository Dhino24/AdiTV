package com.aditv.data.football

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Matchs du jour (cache en mémoire pour ménager les 100 requêtes quotidiennes de la clé gratuite) et sélection
 * des compétitions qui intéressent Adama : Ligue des champions, grands championnats, Ligue 2, CAN, Sénégal, France.
 */
class FootballRepository(private val client: FootballClient, private val clock: () -> Long = System::currentTimeMillis) {
    private class Entry(val at: Long, val list: List<Fixture>)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val goalsCache = ConcurrentHashMap<Long, Pair<Long, List<Goal>>>()

    companion object {
        val zone: ZoneId = ZoneId.of("Europe/Paris")
        /** Compétitions affichées (identifiants API-Football), dans l'ordre d'affichage. */
        val COMPETITIONS = linkedMapOf(
            2L to "Ligue des champions", 61L to "Ligue 1", 39L to "Premier League", 140L to "LaLiga", 135L to "Serie A",
            78L to "Bundesliga", 3L to "Ligue Europa", 848L to "Ligue Europa Conférence", 62L to "Ligue 2", 6L to "Coupe d'Afrique des nations",
            29L to "Qualif. Coupe du monde (Afrique)", 32L to "Qualif. Coupe du monde (Europe)", 5L to "Ligue des nations", 10L to "Matchs amicaux",
        )
        /** Équipes suivies (toujours affichées, en tête avec « Mes équipes »). */
        private val followed = listOf("barcelona", "real madrid", "paris saint germain", "manchester city", "aston villa", "senegal")
        fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex(" +"), " ").trim()
        fun isFollowed(f: Fixture) = listOf(f.home.name, f.away.name).any { n -> followed.any { norm(n).startsWith(it) || norm(n) == it } }
        private fun nationalTeam(f: Fixture) = listOf(f.home.name, f.away.name).any { norm(it) in setOf("france", "senegal") }
        fun keep(f: Fixture) = f.competition.id in COMPETITIONS || f.competition.country.equals("Senegal", true) || nationalTeam(f) || isFollowed(f)
        fun competitionRank(f: Fixture) = COMPETITIONS.keys.indexOf(f.competition.id).let { if (it < 0) 50 else it }
    }

    /** Date du jour à Paris décalée de [offset] jours, au format attendu par l'API. */
    fun date(offset: Int): LocalDate = LocalDate.now(zone).plusDays(offset.toLong())

    /** Matchs retenus d'une date : cache 5 min s'il y a du direct, 30 min aujourd'hui, 6 h sinon. */
    suspend fun fixtures(offset: Int, force: Boolean = false): List<Fixture> {
        val d = date(offset).toString()
        val now = clock()
        cache[d]?.let { e ->
            val ttl = when { e.list.any { it.live } -> 5 * 60_000L; offset == 0 -> 30 * 60_000L; else -> 6 * 3_600_000L }
            if (!force && now - e.at < ttl) return e.list
        }
        val list = client.fixtures(d).filter(::keep).sortedWith(compareBy({ if (isFollowed(it)) 0 else 1 }, ::competitionRank, { it.kickoff }))
        cache[d] = Entry(now, list)
        return list
    }

    suspend fun goals(f: Fixture): List<Goal> {
        if (!f.live && !f.finished) return emptyList()
        goalsCache[f.id]?.takeIf { f.finished || clock() - it.first < 5 * 60_000L }?.let { return it.second }
        return client.goals(f.id).also { goalsCache[f.id] = clock() to it }
    }

    /** Démonstration (sans clé) : matchs fictifs, équipes inventées. */
    fun demo(offset: Int): List<Fixture> {
        val day = date(offset).atTime(21, 0).atZone(zone).toInstant().toEpochMilli()
        val ucl = Competition(2, "Ligue des champions (démo)", "World", null, null)
        val l1 = Competition(61, "Ligue 1 (démo)", "France", null, null)
        val sn = Competition(9001, "Ligue 1 Sénégal (démo)", "Senegal", null, null)
        fun t(id: Long, n: String) = Team(id, n, null)
        return listOf(
            Fixture(1, ucl, "Phase de ligue - 5", day - 2 * 3_600_000L, if (offset == 0) "2H" else "NS", if (offset == 0) 67 else null, t(1, "Lions de Paris"), t(2, "Munich Rouge"), if (offset == 0) 2 else null, if (offset == 0) 1 else null),
            Fixture(2, ucl, "Phase de ligue - 5", day, "NS", null, t(3, "Catalans FC"), t(4, "City Bleu"), null, null),
            Fixture(3, l1, "Journée 14", day - 30 * 60_000L, "NS", null, t(5, "Olympique Sud"), t(6, "Nord United"), null, null),
            Fixture(4, sn, "Journée 9", day - 3 * 3_600_000L - 30 * 60_000L, "NS", null, t(7, "Lions de Dakar"), t(8, "Étoile de Thiès"), null, null),
        )
    }
}
