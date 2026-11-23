package com.aditv.ui.live

import com.aditv.data.db.CatalogCard
import com.aditv.data.live.ChannelFamilies
import com.aditv.data.live.ChannelPlace
import com.aditv.data.live.Family
import com.aditv.data.variants.collapsed
import com.aditv.di.AppContainer
import kotlinx.coroutines.flow.first

data class FamilyEntry(val key: String, val label: String, val count: Int)

/** Match de Ligue des champions trouvé dans le guide, avec la meilleure chaîne qui le diffuse (française d'abord). */
data class ChampionsMatch(val title: String, val startUtc: Long, val endUtc: Long, val channel: CatalogCard)

/**
 * Chaînes du Direct rangées par famille et par diffuseur (France et Sénégal d'abord). Calcul en arrière-plan,
 * mémorisé tant que la liste des chaînes et les corrections manuelles ne changent pas.
 */
object LiveCatalog {
    const val FAV = "fav"
    const val UCL = "ucl"
    private class Index(val stamp: String, val placed: List<Pair<CatalogCard, ChannelPlace>>)
    @Volatile private var cache: Index? = null

    /** Ordre des diffuseurs dans chaque famille (les autres suivent, par ordre alphabétique). */
    private val priority = listOf(
        "beIN SPORTS", "Canal+", "RMC Sport", "Eurosport", "L'Équipe", "Canal+ Afrique", "New World TV",
        "Groupe TF1", "France Télévisions", "Groupe M6", "OCS", "Ciné+", "BFM", "RMC", "RTS",
    )
    /** Pays du reste du monde : francophones d'abord. */
    private val countryOrder = listOf("Belgique", "Suisse", "Canada", "Maroc", "Algérie", "Tunisie")

    private suspend fun index(c: AppContainer, accountId: String): Index {
        val prefs = c.prefs.variantPrefs.first()
        val overrides = c.prefs.channelFamilyOverrides.first()
        val raw = c.db.catalog().allChannels(accountId)
        val stamp = "$accountId|${raw.size}|${raw.firstOrNull()?.id}|${raw.lastOrNull()?.id}|${overrides.hashCode()}|${prefs.hashCode()}"
        cache?.takeIf { it.stamp == stamp }?.let { return it }
        val names = c.db.catalog().categories(accountId, "LIVE").first().associate { it.id to it.name }
        // Logos : ceux du fournisseur, sinon ceux du guide TV (beaucoup de chaînes n'en ont pas chez le fournisseur).
        val icons = runCatching { c.db.live().epgChannelsWithIcon(accountId) }.getOrDefault(emptyList()).associate { it.channelId to it.iconUrl }
        val channels = raw.collapsed(prefs).map { it.first }.distinctBy { it.id }
            .map { ch -> if (ch.imageUrl == null && ch.epgChannelId != null) icons[ch.epgChannelId]?.let { ch.copy(imageUrl = it) } ?: ch else ch }
        val placed = channels.map { ch ->
            val p = ChannelFamilies.place(ch.title, ch.categoryId?.let(names::get), ch.lang)
            val forced = overrides[ch.id]?.let { k -> Family.entries.firstOrNull { it.name == k } }
            ch to (if (forced != null) p.copy(family = forced) else p)
        }
        return Index(stamp, placed).also { cache = it }
    }

    private fun sorted(list: List<Pair<CatalogCard, ChannelPlace>>): List<Pair<CatalogCard, ChannelPlace>> = list.sortedWith(
        compareBy<Pair<CatalogCard, ChannelPlace>>(
            { if (it.second.family == Family.MONDE) countryOrder.indexOf(it.second.country).let { i -> if (i < 0) 99 else i } else 0 },
            { if (it.second.family == Family.MONDE) it.second.country ?: "~" else "" },
            { priority.indexOf(it.second.broadcaster).let { i -> if (i < 0) 99 else i } },
            { it.second.broadcaster.lowercase() },
            { if (it.second.french) 0 else 1 },
            { it.second.number },
            { it.first.title.lowercase() },
        ),
    )

    suspend fun families(c: AppContainer, accountId: String): List<FamilyEntry> {
        val idx = index(c, accountId)
        val favCount = c.db.live().favoriteIds(accountId).first().size
        val counts = idx.placed.groupingBy { it.second.family }.eachCount()
        val ucl = idx.placed.count { it.second.ucl && it.second.family != Family.MONDE }
        return buildList {
            if (favCount > 0) add(FamilyEntry(FAV, "★ Favoris", favCount))
            add(FamilyEntry(UCL, "Ligue des champions", ucl))
            Family.entries.sortedBy { it.order }.forEach { f -> counts[f]?.let { add(FamilyEntry("fam:${f.name}", f.label, it)) } }
        }
    }

    /** Chaînes d'une famille, rangées ; le diffuseur sert d'intertitre. */
    suspend fun channels(c: AppContainer, accountId: String, key: String): List<Pair<CatalogCard, ChannelPlace>> {
        val idx = index(c, accountId)
        return when {
            key == FAV -> {
                val favs = c.db.live().favoriteIds(accountId).first().toSet()
                sorted(idx.placed.filter { it.first.id in favs })
            }
            // Diffuseurs de la Ligue des champions : France (Canal+, M6) puis Afrique francophone (Canal+ Afrique, New World TV).
            key == UCL -> sorted(idx.placed.filter { it.second.ucl && it.second.family != Family.MONDE })
                .sortedBy { if (it.second.french) 0 else 1 }
            key.startsWith("fam:") -> sorted(idx.placed.filter { it.second.family.name == key.removePrefix("fam:") })
            else -> sorted(idx.placed)
        }
    }

    /** Matchs de Ligue des champions des 8 prochains jours (et en cours), chacun sur sa meilleure chaîne. */
    suspend fun championsMatches(c: AppContainer, accountId: String): List<ChampionsMatch> {
        val idx = index(c, accountId)
        val now = System.currentTimeMillis()
        val progs = c.db.live().championsProgrammes(accountId, now, now + 8 * 86_400_000L)
            .filter { ChannelFamilies.isChampionsLeague(it.title, it.category) }
        val mappings = c.db.live().mappings(accountId).associate { it.itemId to it.epgChannelId }
        val byEpg = idx.placed.groupBy { (ch, _) -> mappings[ch.id] ?: ch.epgChannelId }
        return progs.mapNotNull { p ->
            val best = byEpg[p.channelId].orEmpty().minWithOrNull(compareBy({ if (it.second.french) 0 else 1 }, { if (it.second.family == Family.MONDE) 1 else 0 }))
                ?: return@mapNotNull null
            ChampionsMatch(p.title, p.startUtc, p.endUtc, best.first)
        }.distinctBy { com.aditv.data.sport.SportMatcher.matchKey(it.title, it.startUtc) }.take(40)
    }

    /** Toutes les chaînes rangées (pour FootAdi : retrouver qui diffuse un match). */
    suspend fun placed(c: AppContainer, accountId: String): List<Pair<CatalogCard, ChannelPlace>> = index(c, accountId).placed

    fun invalidate() { cache = null }
}
