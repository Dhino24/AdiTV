package com.aditv.data.provider

import kotlinx.coroutines.delay

/**
 * Source de démonstration 100 % fictive, identifiée « Démo » dans l'interface.
 * Aucun lien de lecture, aucun identifiant TMDB réel. Sert à valider navigation et cache sans abonnement.
 */
class DemoAdapter : ProviderAdapter {
    override val kind = ProviderKind.DEMO

    override suspend fun authenticate(): AccountStatus {
        delay(300)
        return AccountStatus("Démo", null, null, null, null, "Europe/Paris", "demo")
    }

    override suspend fun categories(type: MediaType): List<RawCategory> = cats.getValue(type)
        .mapIndexed { i, (id, name) -> RawCategory(id, name, i) }

    override suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit) {
        items(type).chunked(batchSize).forEach { delay(120); onBatch(it) }
    }

    override suspend fun seriesEpisodes(seriesProviderId: String): List<RawEpisode> {
        delay(150)
        return (1..2).flatMap { s ->
            (1..(if (s == 1) 6 else 4)).map { e ->
                RawEpisode("$seriesProviderId-s${s}e$e", s, e, "Épisode $e", "Épisode fictif de démonstration.", null, 2700, null, null)
            }
        }
    }

    /** Démo : aucune lecture possible, aucun flux tiers. */
    override fun streamUrl(target: StreamTarget): String? = null
    override fun xmltvUrl(): String? = null
    override suspend fun shortEpg(streamProviderId: String): List<RawShortProgramme> = emptyList()

    /** Guide fictif autour de l'heure courante pour les chaînes de démo ayant un ID de guide. */
    override fun inlineXmltv(nowMs: Long): String {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z").withZone(java.time.ZoneId.of("Europe/Paris"))
        val slot = 30 * 60_000L
        val start0 = nowMs - nowMs % slot - 4 * slot
        val titles = listOf("Journal fictif", "Magazine fictif", "Documentaire fictif", "Série fictive", "Film fictif", "Débat fictif")
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv generator-info-name=\"ADITV démo\">")
        sb.append("<channel id=\"aditv.demo\"><display-name lang=\"fr\">ADITV Démo</display-name></channel>")
        sb.append("<channel id=\"info24.demo\"><display-name lang=\"fr\">Info 24 (démo)</display-name></channel>")
        for (ch in listOf("aditv.demo", "info24.demo")) for (i in 0 until 40) {
            val s = start0 + i * slot * (if (ch == "aditv.demo") 2 else 1)
            val e = s + slot * (if (ch == "aditv.demo") 2 else 1)
            sb.append("<programme channel=\"$ch\" start=\"${fmt.format(java.time.Instant.ofEpochMilli(s))}\" stop=\"${fmt.format(java.time.Instant.ofEpochMilli(e))}\">")
            sb.append("<title lang=\"fr\">${titles[(i + ch.length) % titles.size]} n°${i + 1}</title><desc lang=\"fr\">Programme fictif de démonstration, aucune diffusion réelle.</desc></programme>")
        }
        return sb.append("</tv>").toString()
    }

    private val cats = mapOf(
        MediaType.LIVE to listOf("l1" to "DÉMO | Information", "l2" to "DÉMO | Sport", "l3" to "DÉMO | Jeunesse", "l4" to "DÉMO | Culture"),
        MediaType.MOVIE to listOf("m1" to "FR| Thrillers", "m2" to "FR| Comédies", "m3" to "FR| Science-fiction", "m4" to "FR| Documentaires"),
        MediaType.SERIES to listOf("s1" to "FR| Séries drame", "s2" to "FR| Séries policières"),
    )

    private fun items(type: MediaType): List<RawItem> {
        val base = 1_763_500_000L // novembre 2025, dates d'ajout fictives
        val src: List<Triple<String, String, String?>> = when (type) {
            MediaType.LIVE -> listOf(
                Triple("ADITV Démo", "l1", "aditv.demo"), Triple("Info 24 (démo)", "l1", "info24.demo"), Triple("Info 24 HD (démo)", "l1", "info24.demo"), Triple("FR| Sport Plus SD (démo)", "l2", null),
                Triple("FR| Sport Plus FHD (démo)", "l2", null), Triple("FR| Sport Plus 4K (démo)", "l2", null), Triple("Kids Doux (démo)", "l3", null), Triple("Doc Kultur (démo)", "l4", null),
                Triple("Atlantique TV (démo)", "l4", null),
            )
            MediaType.MOVIE -> listOf(
                Triple("FR| Les Ombres de Dakar (2026) VOSTFR", "m1", "Thriller"), Triple("FR| Les Ombres de Dakar (2026) VF 4K", "m1", "Thriller"), Triple("FR| Fjord noir (2024)", "m1", "Thriller"),
                Triple("FR| Le Dernier Ferry (2023) 4K", "m1", "Thriller"), Triple("FR| Nuit blanche à Lisbonne (2022)", "m1", "Policier"),
                Triple("FR| Le Grand Déménagement (2025)", "m2", "Comédie"), Triple("FR| Tonton au volant (2021) HD", "m2", "Comédie"),
                Triple("FR| Mariage à la ferme (2024)", "m2", "Comédie"), Triple("FR| Orbite basse (2026) MULTI", "m3", "Science-fiction"),
                Triple("FR| Station Kepler (2023)", "m3", "Science-fiction"), Triple("FR| Les Jardins de Mars (2025)", "m3", "Science-fiction"),
                Triple("FR| Glaciers (2024)", "m4", "Documentaire"), Triple("FR| Forêts primaires (2025)", "m4", "Documentaire"),
                Triple("FR| Les Grands Fleuves (2022)", "m4", "Documentaire"),
            )
            MediaType.SERIES -> listOf(
                Triple("FR| Les Fils du Sable", "s1", "Drame"), Triple("FR| Le Phare des îles", "s1", "Drame"),
                Triple("FR| Les Saisons de Lisbonne", "s1", "Drame"), Triple("FR| Néon Tokyo", "s2", "Policier"),
                Triple("FR| Brigade Atlantique", "s2", "Policier"),
            )
        }
        return src.mapIndexed { i, (title, cat, extra) ->
            RawItem(
                providerId = "${type.name.lowercase()}-$i", type = type, rawTitle = title, position = i + 1,
                categoryProviderId = cat, imageUrl = null, providerTmdbId = null, providerRating = null,
                addedAtEpochSec = base + i * 86_400L, containerExt = null,
                epgChannelId = if (type == MediaType.LIVE) extra else null, catchupDays = null,
                plot = if (type != MediaType.LIVE) "Contenu fictif de démonstration, sans lien de lecture." else null,
                genre = if (type != MediaType.LIVE) extra else null, releaseYear = null,
            )
        }
    }
}
