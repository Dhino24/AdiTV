package com.aditv.ui.foot

import com.aditv.data.db.CatalogCard
import com.aditv.data.football.Fixture
import com.aditv.data.football.FootballRepository
import com.aditv.data.live.ChannelFamilies
import com.aditv.data.live.ChannelPlace
import com.aditv.data.live.Family
import com.aditv.di.AppContainer
import com.aditv.ui.live.LiveCatalog

/** Chaîne de l'abonnement qui diffuse un match : trouvée dans le guide, ou diffuseur habituel de la compétition. */
data class Broadcast(val channel: CatalogCard, val name: String, val fromGuide: Boolean, val french: Boolean)

object Broadcasts {
    /** Mots génériques ignorés pour reconnaître une équipe dans un titre du guide. */
    private val stop = setOf("fc", "cf", "ac", "as", "sc", "afc", "club", "de", "la", "le", "des", "city", "united", "sporting", "olympique", "stade", "real", "saint", "athletic", "sport", "football")
    private val aliases = mapOf(
        "paris saint germain" to listOf("psg", "paris"), "manchester city" to listOf("man city", "manchester city"),
        "manchester united" to listOf("man united", "manchester united", "man utd"), "barcelona" to listOf("barcelone", "barca", "barcelona"),
        "bayern munchen" to listOf("bayern"), "bayern munich" to listOf("bayern"), "inter" to listOf("inter"), "real madrid" to listOf("real madrid"),
        "atletico madrid" to listOf("atletico"), "olympique marseille" to listOf("marseille", "om"), "olympique lyonnais" to listOf("lyon", "ol"),
        "senegal" to listOf("senegal"), "aston villa" to listOf("aston villa", "villa"),
    )

    fun tokens(team: String): List<String> {
        val n = FootballRepository.norm(team)
        aliases.entries.firstOrNull { n.startsWith(it.key) }?.let { return it.value }
        val words = n.split(' ').filter { it.length >= 4 && it !in stop }
        return words.ifEmpty { listOf(n) }
    }

    fun mentions(title: String, team: String): Boolean = mentionsNorm(" " + FootballRepository.norm(title) + " ", tokens(team))

    /** [t] : titre déjà normalisé et entouré d'espaces. */
    private fun mentionsNorm(t: String, toks: List<String>) = toks.any { k -> t.contains(" $k ") || (k.length >= 5 && t.contains(k)) }

    /** Diffuseurs habituels en France (2026-2027), par compétition : utilisés seulement si le guide ne dit rien. */
    private val usual = mapOf(
        2L to Regex("canal"), 3L to Regex("canal"), 848L to Regex("canal"), 39L to Regex("canal"),
        140L to Regex("bein"), 135L to Regex("bein"), 78L to Regex("bein"), 62L to Regex("bein"), 6L to Regex("bein"),
        61L to Regex("ligue ?1|bein|dazn"),
    )

    suspend fun find(c: AppContainer, accountId: String, f: Fixture): List<Broadcast> = findAll(c, accountId, listOf(f))[f.id].orEmpty()

    /**
     * Toutes les chaînes de tous les matchs en UN passage : chaînes, correspondances et guide lus une seule fois,
     * titres du guide normalisés une seule fois (avant : tout était refait pour chaque match → très lent sur la Stick).
     */
    suspend fun findAll(c: AppContainer, accountId: String, fixtures: List<Fixture>): Map<Long, List<Broadcast>> {
        if (fixtures.isEmpty()) return emptyMap()
        val placed = LiveCatalog.placed(c, accountId)
        val mappings = c.db.live().mappings(accountId).associate { it.itemId to it.epgChannelId }
        val byEpg = placed.groupBy { (ch, _) -> mappings[ch.id] ?: ch.epgChannelId }
        val from = fixtures.minOf { it.kickoff } - 45 * 60_000L
        val to = fixtures.maxOf { it.kickoff } + 30 * 60_000L
        // Seulement les programmes des chaînes de l'abonnement, titres normalisés une fois.
        val progs = c.db.live().programmesStarting(accountId, from, to)
            .filter { byEpg.containsKey(it.channelId) }
            .map { Triple(it.startUtc, it.channelId, " " + FootballRepository.norm(it.title) + " ") }
        val sport = placed.filter { it.second.family == Family.SPORT_FR }.map { it to FootballRepository.norm(it.first.title) }
        return fixtures.associate { f ->
            val home = tokens(f.home.name); val away = tokens(f.away.name)
            val hits = progs.filter { (start, _, t) -> start in (f.kickoff - 45 * 60_000L)..(f.kickoff + 30 * 60_000L) && mentionsNorm(t, home) && mentionsNorm(t, away) }
                .flatMap { byEpg[it.second].orEmpty() }
            val fromGuide = rank(hits).map { Broadcast(it.first, ChannelFamilies.cleanName(it.first.title), true, it.second.french) }.distinctBy { it.channel.id }.take(6)
            f.id to fromGuide.ifEmpty {
                val r = usual[f.competition.id]
                if (r == null) emptyList()
                else rank(sport.filter { r.containsMatchIn(it.second) }.map { it.first }).take(4)
                    .map { Broadcast(it.first, ChannelFamilies.cleanName(it.first.title), false, it.second.french) }
            }
        }
    }

    private fun rank(list: List<Pair<CatalogCard, ChannelPlace>>) = list.distinctBy { it.first.id }
        .sortedWith(compareBy({ if (it.second.french) 0 else 1 }, { if (it.second.family == Family.MONDE) 1 else 0 }, { it.second.number }))
}
