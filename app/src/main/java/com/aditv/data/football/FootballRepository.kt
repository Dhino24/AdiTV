package com.aditv.data.football

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Matchs du jour (cache en mémoire pour ménager les 100 requêtes quotidiennes de la clé gratuite) et sélection
 * des compétitions qui intéressent Adama : Ligue des champions, grands championnats, Ligue 2, CAN, Sénégal, France.
 */
class FootballRepository(
    private val client: FootballClient,
    /** Cache sur disque (survit au redémarrage) : réponses par date, buts des matchs terminés. */
    private val dir: java.io.File? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val at: Long, val list: List<Fixture>)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val goalsCache = ConcurrentHashMap<Long, Pair<Long, List<Goal>>>()

    companion object {
        val zone: ZoneId = ZoneId.of("Europe/Paris")
        const val SAVE_BELOW = 10
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

    /** Requêtes restantes aujourd'hui (null tant qu'aucun appel n'a eu lieu) et limite du jour. */
    val remaining get() = client.remaining
    val dailyLimit get() = client.dailyLimit
    /** Sous ce seuil : plus de rafraîchissement automatique ni de buteurs, seulement ce que l'utilisateur demande. */
    val saving get() = (client.remaining ?: Int.MAX_VALUE) < SAVE_BELOW

    private fun file(name: String) = dir?.let { java.io.File(it, name) }
    private fun readDisk(d: String): Entry? = runCatching {
        val f = file("fixtures-$d.json")?.takeIf { it.exists() } ?: return null
        Entry(f.lastModified(), sortAndKeep(client.parseFixtures(f.readText())))
    }.getOrNull()
    private fun writeDisk(d: String, raw: String) = runCatching {
        dir?.mkdirs(); file("fixtures-$d.json")?.writeText(raw)
        // Ménage : on garde 10 jours.
        dir?.listFiles()?.filter { clock() - it.lastModified() > 10 * 86_400_000L }?.forEach { it.delete() }
    }

    private fun sortAndKeep(l: List<Fixture>) = l.filter(::keep).sortedWith(compareBy({ if (isFollowed(it)) 0 else 1 }, ::competitionRank, { it.kickoff }))

    /**
     * Matchs retenus d'une date. Une requête par date au plus : jours passés terminés = définitif ;
     * direct = 5 min ; aujourd'hui = 30 min ; à venir = 6 h. Quota presque épuisé : on garde ce qu'on a.
     */
    suspend fun fixtures(offset: Int, force: Boolean = false): List<Fixture> {
        val d = date(offset).toString()
        val now = clock()
        val e = cache[d] ?: readDisk(d)?.also { cache[d] = it }
        if (e != null) {
            val ttl = when {
                e.list.any { it.live } -> 5 * 60_000L
                offset < 0 && e.list.all { it.finished || it.status in setOf("PST", "CANC", "ABD") } -> Long.MAX_VALUE
                offset <= 0 -> 30 * 60_000L
                else -> 6 * 3_600_000L
            }
            if ((!force && now - e.at < ttl) || saving) return e.list
        }
        val raw = client.fixturesRaw(d)
        writeDisk(d, raw)
        return sortAndKeep(client.parseFixtures(raw)).also { cache[d] = Entry(now, it) }
    }

    private val lineupsCache = ConcurrentHashMap<Long, List<Lineup>>()
    /** Dernier essai de compositions pour un match pas encore commencé (on ne redemande pas avant 10 min). */
    private val lineupTried = ConcurrentHashMap<Long, Long>()

    /** Compositions attendues : match commencé/terminé, ou coup d'envoi dans moins de 70 min. */
    private fun lineupsDue(f: Fixture) = f.live || f.finished || (f.kickoff - clock() in 0..70 * 60_000L)

    private fun lineupsKnown(f: Fixture): List<Lineup>? = lineupsCache[f.id] ?: loadLineups(f.id)?.also { lineupsCache[f.id] = it }

    private fun needsRequest(f: Fixture): Boolean {
        val goalsMissing = (f.live || f.finished) && fresh(f) == null
        val lineupMissing = lineupsDue(f) && lineupsKnown(f) == null && clock() - (lineupTried[f.id] ?: 0L) > 10 * 60_000L
        return goalsMissing || lineupMissing
    }

    /** Buts d'un match (voir [details]). */
    suspend fun goals(f: Fixture, sameDay: List<Fixture> = listOf(f)): List<Goal> = details(f, sameDay).goals

    /**
     * Buts et compositions. Tout ce qui manque pour les matchs du jour est demandé d'un coup : 1 requête pour 20 matchs.
     * Compositions : publiées ~1 h avant ; une fois connues, gardées pour toujours (disque). Buts des matchs terminés : disque.
     */
    suspend fun details(f: Fixture, sameDay: List<Fixture> = listOf(f)): MatchDetails {
        if (!needsRequest(f) || saving) return MatchDetails(fresh(f).orEmpty(), lineupsKnown(f).orEmpty())
        val wanted = (listOf(f) + sameDay.filter { it.id != f.id && needsRequest(it) }).distinctBy { it.id }.take(20)
        val got = client.detailsBatchRaw(wanted.map { it.id })
        val now = clock()
        wanted.forEach { w ->
            val (g, rawLineups) = got[w.id] ?: return@forEach
            if (w.live || w.finished) { goalsCache[w.id] = now to g; if (w.finished) saveGoals(w.id, g) }
            val l = client.parseLineups(rawLineups)
            // Connues, ou match terminé sans composition publiée : on ne redemandera plus.
            if (l.isNotEmpty() || w.finished) { lineupsCache[w.id] = l; runCatching { dir?.mkdirs(); file("lineups-${w.id}.json")?.writeText(rawLineups) } }
            else lineupTried[w.id] = now
        }
        return MatchDetails(fresh(f).orEmpty(), lineupsKnown(f).orEmpty())
    }

    private fun loadLineups(id: Long): List<Lineup>? = runCatching {
        file("lineups-$id.json")?.takeIf { it.exists() }?.readText()?.let(client::parseLineups)
    }.getOrNull()

    private fun fresh(f: Fixture): List<Goal>? {
        goalsCache[f.id]?.takeIf { f.finished || clock() - it.first < 5 * 60_000L }?.let { return it.second }
        if (f.finished) loadGoals(f.id)?.let { goalsCache[f.id] = clock() to it; return it }
        return null
    }

    // Format simple : une ligne par but « minute|équipe|joueur|détail ».
    private fun saveGoals(id: Long, g: List<Goal>) = runCatching {
        dir?.mkdirs(); file("goals-$id.txt")?.writeText(g.joinToString("\n") { listOf(it.minute ?: "", it.teamId, it.player.replace("|", " "), it.detail.orEmpty().replace("|", " ")).joinToString("|") })
    }
    private fun loadGoals(id: Long): List<Goal>? = runCatching {
        val f = file("goals-$id.txt")?.takeIf { it.exists() } ?: return null
        f.readLines().filter { it.isNotBlank() }.map { l -> val p = l.split("|"); Goal(p[0].toIntOrNull(), p[2], p[1].toLongOrNull() ?: 0, p.getOrNull(3)?.ifBlank { null }) }
    }.getOrNull()

    /** Démonstration : compositions fictives. */
    fun demoLineups(f: Fixture): List<Lineup> {
        if (!f.live && !f.finished && f.kickoff - clock() > 70 * 60_000L) return emptyList()
        val pos = listOf("G", "D", "D", "D", "D", "M", "M", "M", "F", "F", "F")
        fun team(t: Team, formation: String) = Lineup(t.id, t.name, formation, "Coach ${t.name.split(' ').last()}",
            pos.mapIndexed { i, p -> LineupPlayer(if (i == 0) 1 else i + 1, "Joueur ${i + 1} (démo)", p) },
            (12..18).map { LineupPlayer(it, "Remplaçant ${it - 11}", null) })
        return listOf(team(f.home, "4-3-3"), team(f.away, "4-2-3-1"))
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
