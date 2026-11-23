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

/** Joueur : [grid] = « rangée:colonne » sur le terrain (1 = gardien), donné par l'API pour les titulaires. */
data class LineupPlayer(val number: Int?, val name: String, val pos: String?, val id: Long = 0, val grid: String? = null) {
    /** Photo (serveur d'images d'API-Football, hors quota de requêtes). */
    val photo get() = if (id > 0) "https://media.api-sports.io/football/players/$id.png" else null
}
/** Composition d'une équipe (publiée environ 1 h avant le coup d'envoi) ; couleurs du maillot si l'API les donne. */
data class Lineup(
    val teamId: Long, val team: String, val formation: String?, val coach: String?, val startXI: List<LineupPlayer>, val substitutes: List<LineupPlayer>,
    val shirt: Long? = null, val shirtNumber: Long? = null,
)
/** Buts + compositions d'un match. */
data class MatchDetails(val goals: List<Goal>, val lineups: List<Lineup>)

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

    /** Requêtes restantes aujourd'hui / limite du jour (en-têtes de l'API, mis à jour à chaque appel). */
    @Volatile var remaining: Int? = null; private set
    @Volatile var dailyLimit: Int? = null; private set

    private suspend fun get(path: String): JsonObject = parse(getRaw(path))

    /** Réponse brute (pour le cache sur disque) ; les erreurs de l'API sont levées ici aussi. */
    suspend fun getRaw(path: String): String = withContext(Dispatchers.IO) {
        val k = key() ?: throw FootballError("Aucune clé API-Football : Réglages → FootAdi.")
        val req = Request.Builder().url(base + path).header("x-apisports-key", k).build()
        http.newCall(req).execute().use { r ->
            r.header("x-ratelimit-requests-remaining")?.toIntOrNull()?.let { remaining = it }
            r.header("x-ratelimit-requests-limit")?.toIntOrNull()?.let { dailyLimit = it }
            if (!r.isSuccessful) throw FootballError("Service football indisponible (HTTP ${r.code}).")
            val body = r.body.string()
            parse(body)
            body
        }
    }

    private fun parse(body: String): JsonObject {
        run {
            val o = json.parseToJsonElement(body) as? JsonObject ?: throw FootballError("Réponse illisible.")
            // API-Football signale les problèmes de clé ou d'offre dans « errors » (objet ou liste non vide).
            val errors = o["errors"]
            val msg = when (errors) {
                is JsonObject -> errors.values.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
                is JsonArray -> errors.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
                else -> null
            }
            if (!msg.isNullOrBlank()) throw FootballError("API-Football : $msg")
            return o
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
    suspend fun fixtures(date: String): List<Fixture> = parseFixtures(fixturesRaw(date))
    suspend fun fixturesRaw(date: String): String = getRaw("/fixtures?date=$date&timezone=Europe/Paris")
    fun parseFixtures(raw: String): List<Fixture> = (parse(raw)["response"] as? JsonArray).orEmpty().mapNotNull(::parseFixture)

    private fun parseGoal(e: JsonElement): Goal? {
        val o = e.obj() ?: return null
        return Goal((o["time"].obj()?.get("elapsed") as? JsonPrimitive)?.intOrNull, o["player"].obj()?.get("name").str() ?: return null,
            (o["team"].obj()?.get("id") as? JsonPrimitive)?.longOrNull ?: 0, o["detail"].str())
    }

    /** Buts d'un match (1 requête). */
    suspend fun goals(fixtureId: Long): List<Goal> =
        (get("/fixtures/events?fixture=$fixtureId&type=Goal")["response"] as? JsonArray).orEmpty().mapNotNull(::parseGoal)

    /** Buts de 20 matchs au plus en UNE requête (/fixtures?ids=… renvoie les événements de chaque match). */
    suspend fun goalsBatch(ids: List<Long>): Map<Long, List<Goal>> = detailsBatch(ids).mapValues { it.value.goals }

    /** Buts ET compositions de 20 matchs au plus, en UNE requête (/fixtures?ids=…). Compositions brutes à part, pour le disque. */
    suspend fun detailsBatch(ids: List<Long>): Map<Long, MatchDetails> = detailsBatchRaw(ids).mapValues { (_, v) -> MatchDetails(v.first, parseLineups(v.second)) }

    suspend fun detailsBatchRaw(ids: List<Long>): Map<Long, Pair<List<Goal>, String>> {
        if (ids.isEmpty()) return emptyMap()
        val r = get("/fixtures?ids=" + ids.take(20).joinToString("-") + "&timezone=Europe/Paris")["response"] as? JsonArray
        return r.orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = (o["fixture"].obj()?.get("id") as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            id to ((o["events"] as? JsonArray).orEmpty().filter { (it.obj()?.get("type")).str() == "Goal" }.mapNotNull(::parseGoal) to (o["lineups"] ?: JsonArray(emptyList())).toString())
        }.toMap()
    }

    /** Compositions (tableau « lineups » de l'API). */
    fun parseLineups(raw: String): List<Lineup> = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull().orEmpty().mapNotNull { e ->
        val o = e.obj() ?: return@mapNotNull null
        val t = o["team"].obj() ?: return@mapNotNull null
        fun players(k: String) = (o[k] as? JsonArray).orEmpty().mapNotNull { pe ->
            val p = pe.obj()?.get("player").obj() ?: return@mapNotNull null
            LineupPlayer((p["number"] as? JsonPrimitive)?.intOrNull, p["name"].str() ?: return@mapNotNull null, p["pos"].str(),
                (p["id"] as? JsonPrimitive)?.longOrNull ?: 0, p["grid"].str())
        }
        fun color(k: String) = t["colors"].obj()?.get("player").obj()?.get(k).str()?.removePrefix("#")?.toLongOrNull(16)
        Lineup((t["id"] as? JsonPrimitive)?.longOrNull ?: 0, t["name"].str() ?: "?", o["formation"].str(), o["coach"].obj()?.get("name").str(), players("startXI"), players("substitutes"),
            color("primary"), color("number"))
    }.filter { it.startXI.isNotEmpty() }

    /** Vérifie la clé (statut du compte). */
    suspend fun check(): String {
        val r = get("/status")["response"].obj()
        val plan = r?.get("subscription").obj()?.get("plan").str()
        val used = r?.get("requests").obj()?.get("current")?.let { (it as? JsonPrimitive)?.intOrNull }
        val limit = r?.get("requests").obj()?.get("limit_day")?.let { (it as? JsonPrimitive)?.intOrNull }
        if (used != null && limit != null) { remaining = limit - used; dailyLimit = limit }
        return "Clé valide" + (plan?.let { " · offre $it" } ?: "") + (if (used != null && limit != null) " · $used / $limit requêtes aujourd'hui" else "")
    }

    @Suppress("unused") private fun io(): Nothing = throw IOException()
}
