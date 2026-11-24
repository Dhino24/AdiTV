package com.aditv.data.football

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Classements via football-data.org (offre gratuite à vie, clé personnelle) : l'offre gratuite d'API-Football
 * ne donne pas les classements de la saison en cours. Couvre Ligue 1, Premier League, LaLiga, Serie A, Bundesliga,
 * Ligue des champions (pas Ligue 2, Europa, Conférence). Limite gratuite : 10 requêtes par minute.
 */
class FootballDataClient(private val http: OkHttpClient, private val key: () -> String?, private val base: String = "https://api.football-data.org/v4") {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Identifiant API-Football → code football-data.org. */
        val CODES = mapOf(61L to "FL1", 39L to "PL", 140L to "PD", 135L to "SA", 78L to "BL1", 2L to "CL")

        /** Zones indicatives (football-data ne les donne pas) : position → description, comme celles d'API-Football. */
        fun zone(league: Long, rank: Int, size: Int): String? = when (league) {
            2L -> when { rank <= 8 -> "Champions League (8es de finale)"; rank <= 24 -> "Champions League (barrages)"; else -> "Éliminé" }
            61L -> when { rank <= 3 -> "Champions League"; rank == 4 -> "Champions League (qualifications)"; rank == 5 -> "Europa League"; rank == 6 -> "Conference League"; rank == size - 2 -> "Barrage de relégation"; rank > size - 2 -> "Relegation"; else -> null }
            78L -> when { rank <= 4 -> "Champions League"; rank == 5 -> "Europa League"; rank == 6 -> "Conference League"; rank == size - 2 -> "Barrage de relégation"; rank > size - 2 -> "Relegation"; else -> null }
            else -> when { rank <= 4 -> "Champions League"; rank == 5 -> "Europa League"; rank == 6 -> "Conference League"; rank > size - 3 -> "Relegation"; else -> null }
        }
    }

    suspend fun standingsRaw(league: Long): String = withContext(Dispatchers.IO) {
        val code = CODES[league] ?: throw FootballError("Classement non couvert par football-data.org.")
        val k = key() ?: throw FootballError("Aucune clé football-data.org : Réglages → FootAdi.")
        val req = Request.Builder().url("$base/competitions/$code/standings").header("X-Auth-Token", k).build()
        http.newCall(req).execute().use { r ->
            val body = r.body.string()
            when (r.code) {
                200 -> body
                400, 401, 403 -> throw FootballError("football-data.org refuse la clé ou la compétition (HTTP ${r.code}).")
                429 -> throw FootballError("football-data.org : trop de requêtes, réessayez dans une minute.")
                else -> throw FootballError("football-data.org indisponible (HTTP ${r.code}).")
            }
        }
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.int() = (this as? JsonPrimitive)?.intOrNull ?: 0

    /** Tableau « TOTAL » (et ses poules s'il y en a), au format commun [StandingRow]. */
    fun parseStandings(league: Long, raw: String): List<StandingRow> {
        val root = json.parseToJsonElement(raw).obj() ?: return emptyList()
        val tables = (root["standings"] as? JsonArray).orEmpty().mapNotNull { it.obj() }.filter { (it["type"].str() ?: "TOTAL") == "TOTAL" }
        return tables.flatMap { t ->
            val rows = (t["table"] as? JsonArray).orEmpty().mapNotNull { it.obj() }
            val group = t["group"].str()?.replace("GROUP_", "Groupe ")
            rows.mapNotNull { o ->
                val team = o["team"].obj() ?: return@mapNotNull null
                val rank = o["position"].int()
                StandingRow(
                    rank, Team((team["id"] as? JsonPrimitive)?.longOrNull ?: 0, team["shortName"].str() ?: team["name"].str() ?: "?", team["crest"].str()),
                    o["points"].int(), o["playedGames"].int(), o["won"].int(), o["draw"].int(), o["lost"].int(), o["goalDifference"].int(),
                    o["form"].str()?.replace(",", ""), group, zone(league, rank, rows.size),
                )
            }
        }
    }

    suspend fun check(): String {
        val raw = standingsRaw(61L)
        return "Clé valide · Ligue 1 : ${parseStandings(61L, raw).size} équipes"
    }
}
