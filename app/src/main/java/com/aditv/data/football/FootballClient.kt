package com.aditv.data.football

import com.aditv.core.Redactor
import com.aditv.security.ProviderSecret
import com.aditv.security.SecretStore
import java.io.IOException
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

data class Team(val id: Long, val name: String, val logo: String?)
data class Competition(val id: Long, val name: String, val country: String, val flag: String?, val logo: String?)
data class Fixture(
    val id: Long, val competition: Competition, val round: String?, val kickoff: Long,
    /** NS à venir, 1H/HT/2H/ET/P en cours, FT/AET/PEN terminé, PST/CANC… */
    val status: String, val elapsed: Int?, val home: Team, val away: Team, val homeGoals: Int?, val awayGoals: Int?,
) {
    val live get() = status in setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT")
    val finished get() = status in setOf("FT", "AET", "PEN")
}
data class Goal(val minute: Int?, val player: String, val teamId: Long, val detail: String?)

class FootballError(message: String) : Exception(message)

/** Clé API-Football personnelle, chiffrée sur l'appareil comme la clé TMDB. */
class FootballKeyStore(private val secrets: SecretStore) {
    @Volatile private var cached: String? = null
    @Volatile private var loaded = false
    fun get(): String? { if (!loaded) { cached = secrets.get(REF)?.password; loaded = true; Redactor.registerSecret(cached) }; return cached }
    fun set(key: String) { val k = key.trim(); secrets.put(REF, ProviderSecret("football", k)); cached = k; loaded = true; Redactor.registerSecret(k) }
    fun clear() { secrets.remove(REF); cached = null; loaded = true }
    private companion object { const val REF = "football:key" }
}

/**
 * Données football (matchs, écussons, scores, buteurs) via API-Football (api-sports.io), avec la clé gratuite de
 * l'utilisateur. Les chaînes qui diffusent ne viennent pas d'ici : elles sont retrouvées dans le guide TV de l'abonnement.
 */
class FootballClient(private val http: OkHttpClient, private val key: () -> String?, private val base: String = "https://v3.football.api-sports.io") {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(path: String): JsonObject = withContext(Dispatchers.IO) {
        val k = key() ?: throw FootballError("Aucune clé API-Football : Réglages → FootAdi.")
        val req = Request.Builder().url(base + path).header("x-apisports-key", k).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw FootballError("Service football indisponible (HTTP ${r.code}).")
            val o = json.parseToJsonElement(r.body.string()) as? JsonObject ?: throw FootballError("Réponse illisible.")
            // API-Football signale les problèmes de clé ou d'offre dans « errors » (objet ou liste non vide).
            val errors = o["errors"]
            val msg = when (errors) {
                is JsonObject -> errors.values.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
                is JsonArray -> errors.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
                else -> null
            }
            if (!msg.isNullOrBlank()) throw FootballError("API-Football : $msg")
            o
        }
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it != "null" }

    private fun parseFixture(e: JsonElement): Fixture? {
        val o = e.obj() ?: return null
        val f = o["fixture"].obj() ?: return null
        val l = o["league"].obj() ?: return null
        val t = o["teams"].obj() ?: return null
        val g = o["goals"].obj()
        fun team(x: JsonElement?) = x.obj()?.let { Team((it["id"] as? JsonPrimitive)?.longOrNull ?: 0, it["name"].str() ?: "?", it["logo"].str()) }
        return Fixture(
            id = (f["id"] as? JsonPrimitive)?.longOrNull ?: return null,
            competition = Competition((l["id"] as? JsonPrimitive)?.longOrNull ?: 0, l["name"].str() ?: "", l["country"].str() ?: "", l["flag"].str(), l["logo"].str()),
            round = l["round"].str(),
            kickoff = ((f["timestamp"] as? JsonPrimitive)?.longOrNull ?: return null) * 1000,
            status = f["status"].obj()?.get("short").str() ?: "NS",
            elapsed = (f["status"].obj()?.get("elapsed") as? JsonPrimitive)?.intOrNull,
            home = team(t["home"]) ?: return null, away = team(t["away"]) ?: return null,
            homeGoals = (g?.get("home") as? JsonPrimitive)?.intOrNull, awayGoals = (g?.get("away") as? JsonPrimitive)?.intOrNull,
        )
    }

    /** Tous les matchs d'une date (une seule requête pour toutes les compétitions), heure de Paris. */
    suspend fun fixtures(date: String): List<Fixture> =
        (get("/fixtures?date=$date&timezone=Europe/Paris")["response"] as? JsonArray).orEmpty().mapNotNull(::parseFixture)

    /** Buts d'un match. */
    suspend fun goals(fixtureId: Long): List<Goal> =
        (get("/fixtures/events?fixture=$fixtureId&type=Goal")["response"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            Goal((o["time"].obj()?.get("elapsed") as? JsonPrimitive)?.intOrNull, o["player"].obj()?.get("name").str() ?: return@mapNotNull null,
                (o["team"].obj()?.get("id") as? JsonPrimitive)?.longOrNull ?: 0, o["detail"].str())
        }

    /** Vérifie la clé (statut du compte). */
    suspend fun check(): String {
        val r = get("/status")["response"].obj()
        val plan = r?.get("subscription").obj()?.get("plan").str()
        val used = r?.get("requests").obj()?.get("current")?.let { (it as? JsonPrimitive)?.intOrNull }
        val limit = r?.get("requests").obj()?.get("limit_day")?.let { (it as? JsonPrimitive)?.intOrNull }
        return "Clé valide" + (plan?.let { " · offre $it" } ?: "") + (if (used != null && limit != null) " · $used / $limit requêtes aujourd'hui" else "")
    }

    @Suppress("unused") private fun io(): Nothing = throw IOException()
}
