package com.aditv.data.provider

import com.aditv.core.AppLog
import com.aditv.net.CleartextNotAllowedException
import com.aditv.net.CrossHostRedirectException
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Adaptateur pour les serveurs exposant une API de type Xtream (`player_api.php`).
 * La compatibilité est vérifiée à l'exécution, jamais déduite du seul format des identifiants.
 * Les URLs de lecture ne sont pas stockées : elles seront reconstruites à la demande (phase 2).
 */
class XtreamAdapter(
    private val client: OkHttpClient,
    private val creds: ProviderCredentials,
) : ProviderAdapter {
    override val kind = ProviderKind.XTREAM
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun apiUrl(action: String? = null): HttpUrl = creds.endpoint.newBuilder()
        .addPathSegment("player_api.php")
        .addQueryParameter("username", creds.username)
        .addQueryParameter("password", creds.password)
        .apply { if (action != null) addQueryParameter("action", action) }
        .build()

    override suspend fun authenticate(): AccountStatus {
        val root = getJson(apiUrl())
        val obj = root as? JsonObject ?: run {
            // Certains panneaux renvoient [] ou une valeur vide pour des accès refusés.
            if (root.toString().let { it == "[]" || it == "\"\"" }) throw ProviderError.InvalidCredentials()
            throw ProviderError.UnexpectedResponse("objet attendu")
        }
        val user = obj["user_info"] as? JsonObject ?: throw ProviderError.UnexpectedResponse("user_info absent")
        val server = obj["server_info"] as? JsonObject
        if (user.bool("auth") == false) throw ProviderError.InvalidCredentials()
        val status = user.str("status")
        if (status != null && !status.equals("Active", ignoreCase = true)) throw ProviderError.AccountInactive(status)
        return AccountStatus(
            status = status,
            expiresAtEpochSec = user.long("exp_date")?.takeIf { it > 0 },
            maxConnections = user.int("max_connections"),
            activeConnections = user.int("active_cons"),
            isTrial = user.bool("is_trial"),
            serverTimezone = server?.str("timezone"),
            apiShape = "xtream/player_api",
            liveFormats = (user["allowed_output_formats"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.lowercase() }?.takeIf { it.isNotEmpty() },
        )
    }

    override suspend fun categories(type: MediaType): List<RawCategory> {
        val action = when (type) {
            MediaType.LIVE -> "get_live_categories"
            MediaType.MOVIE -> "get_vod_categories"
            MediaType.SERIES -> "get_series_categories"
        }
        val out = ArrayList<RawCategory>()
        streamArray(apiUrl(action)) { el ->
            val o = el as? JsonObject ?: return@streamArray
            val id = o.str("category_id") ?: return@streamArray
            out += RawCategory(id, o.str("category_name") ?: "Sans nom", out.size)
        }
        return out
    }

    override suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit) {
        val action = when (type) {
            MediaType.LIVE -> "get_live_streams"
            MediaType.MOVIE -> "get_vod_streams"
            MediaType.SERIES -> "get_series"
        }
        val batch = ArrayList<RawItem>(batchSize)
        streamArray(apiUrl(action)) { el ->
            val item = (el as? JsonObject)?.let { parseItem(type, it) } ?: return@streamArray
            batch += item
            if (batch.size >= batchSize) { onBatch(batch.toList()); batch.clear() }
        }
        if (batch.isNotEmpty()) onBatch(batch.toList())
    }

    override suspend fun seriesEpisodes(seriesProviderId: String): List<RawEpisode> {
        val root = getJson(apiUrl("get_series_info").newBuilder().addQueryParameter("series_id", seriesProviderId).build())
        return parseSeriesEpisodes(root)
    }

    /** `episodes` est un objet indexé par saison (le plus courant) ou une liste selon les panneaux. */
    internal fun parseSeriesEpisodes(root: JsonElement): List<RawEpisode> {
        val obj = root as? JsonObject ?: return emptyList()
        val raw = obj["episodes"] ?: return emptyList()
        val entries: List<Pair<Int?, JsonElement>> = when (raw) {
            is JsonObject -> raw.entries.flatMap { (season, list) ->
                ((list as? kotlinx.serialization.json.JsonArray) ?: return@flatMap emptyList()).map { season.toIntOrNull() to it }
            }
            is kotlinx.serialization.json.JsonArray -> raw.flatMap { el ->
                if (el is kotlinx.serialization.json.JsonArray) el.map { null to it } else listOf(null to el)
            }
            else -> emptyList()
        }
        return entries.mapNotNull { (seasonKey, el) ->
            val e = el as? JsonObject ?: return@mapNotNull null
            val id = e.str("id") ?: return@mapNotNull null
            val info = e["info"] as? JsonObject
            RawEpisode(
                providerId = id,
                season = e.int("season") ?: seasonKey ?: 0,
                number = e.int("episode_num"),
                title = e.str("title") ?: "Épisode",
                plot = info?.str("plot"),
                imageUrl = info?.str("movie_image")?.takeIf { it.startsWith("http", true) },
                durationSec = info?.int("duration_secs"),
                containerExt = e.str("container_extension"),
                airDate = info?.str("releasedate", "air_date"),
            )
        }.sortedWith(compareBy({ it.season }, { it.number ?: Int.MAX_VALUE }))
    }

    /** Schéma de flux Xtream habituel, sur l'hôte de connexion. Identifiants dans le chemin : URL secrète. */
    override fun streamUrl(target: StreamTarget): String = creds.endpoint.newBuilder().apply {
        when (target) {
            is StreamTarget.Movie -> addPathSegment("movie").addPathSegment(creds.username).addPathSegment(creds.password)
                .addPathSegment("${target.providerId}.${target.containerExt ?: "mp4"}")
            is StreamTarget.Episode -> addPathSegment("series").addPathSegment(creds.username).addPathSegment(creds.password)
                .addPathSegment("${target.providerId}.${target.containerExt ?: "mp4"}")
            is StreamTarget.Live -> addPathSegment("live").addPathSegment(creds.username).addPathSegment(creds.password)
                .addPathSegment("${target.providerId}.$liveExtension")
            // Schéma de replay courant des panneaux Xtream (heure locale du serveur) : expérimental, à valider avec une émission autorisée.
            is StreamTarget.Catchup -> {
                val zone = runCatching { java.time.ZoneId.of(target.serverZone ?: "UTC") }.getOrDefault(java.time.ZoneOffset.UTC)
                val t = java.time.Instant.ofEpochMilli(target.startUtc).atZone(zone)
                val stamp = "%04d-%02d-%02d:%02d-%02d".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
                addPathSegment("timeshift").addPathSegment(creds.username).addPathSegment(creds.password)
                    .addPathSegment(target.durationMin.toString()).addPathSegment(stamp).addPathSegment("${target.providerId}.ts")
            }
        }
    }.build().toString()

    override fun xmltvUrl(): String = creds.endpoint.newBuilder().addPathSegment("xmltv.php")
        .addQueryParameter("username", creds.username).addQueryParameter("password", creds.password).build().toString()

    /** `get_short_epg` : titres et résumés encodés en base64, horaires en secondes UTC. */
    override suspend fun shortEpg(streamProviderId: String): List<RawShortProgramme> {
        val root = getJson(apiUrl("get_short_epg").newBuilder().addQueryParameter("stream_id", streamProviderId).addQueryParameter("limit", "4").build())
        return parseShortEpg(root)
    }

    internal fun parseShortEpg(root: JsonElement): List<RawShortProgramme> {
        val list = (root as? JsonObject)?.get("epg_listings") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        fun b64(s: String?) = s?.let { runCatching { String(java.util.Base64.getDecoder().decode(it), Charsets.UTF_8) }.getOrDefault(it) }?.trim()?.ifEmpty { null }
        return list.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val start = o.long("start_timestamp") ?: return@mapNotNull null
            val stop = o.long("stop_timestamp", "end_timestamp") ?: return@mapNotNull null
            RawShortProgramme(b64(o.str("title")) ?: "Sans titre", b64(o.str("description")), start * 1000, stop * 1000)
        }.filter { it.endUtc > it.startUtc }.sortedBy { it.startUtc }
    }

    /** Format direct : ts si autorisé (ou non communiqué), sinon m3u8. */
    var liveExtension: String = "ts"

    internal fun parseItem(type: MediaType, o: JsonObject): RawItem? {
        val id = (if (type == MediaType.SERIES) o.str("series_id") else o.str("stream_id")) ?: return null
        val name = o.str("name", "title") ?: return null
        return RawItem(
            providerId = id,
            type = type,
            rawTitle = name,
            position = o.int("num"),
            // Panneaux récents : `category_id` vide et seulement une liste `category_ids`.
            categoryProviderId = o.str("category_id") ?: (o["category_ids"] as? kotlinx.serialization.json.JsonArray)
                ?.firstNotNullOfOrNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.takeIf { c -> c.isNotEmpty() && c != "null" } },
            imageUrl = o.str("stream_icon", "cover")?.takeIf { it.startsWith("http", ignoreCase = true) },
            providerTmdbId = o.str("tmdb", "tmdb_id")?.takeIf { it != "0" },
            providerRating = o.str("rating")?.takeIf { it != "0" && it != "0.0" },
            addedAtEpochSec = o.long("added", "last_modified")?.takeIf { it in 946_684_800L..4_102_444_800L },
            containerExt = o.str("container_extension"),
            epgChannelId = if (type == MediaType.LIVE) o.str("epg_channel_id") else null,
            catchupDays = if (type == MediaType.LIVE && o.bool("tv_archive") == true) o.int("tv_archive_duration") else null,
            plot = o.str("plot"),
            genre = o.str("genre"),
            releaseYear = (o.str("year") ?: o.str("releaseDate", "release_date")?.take(4))?.toIntOrNull()?.takeIf { it in 1900..2100 },
        )
    }

    // ---- HTTP ----

    private suspend fun getJson(url: HttpUrl): JsonElement = withContext(Dispatchers.IO) {
        execute(url) { input ->
            try { json.decodeFromString(JsonElement.serializer(), input.bufferedReader().readText()) }
            catch (e: SerializationException) { throw ProviderError.UnexpectedResponse("JSON illisible") }
        }
    }

    /** Lit un tableau JSON élément par élément. Tolère `{}` (vide) et les objets indexés par identifiant. */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun streamArray(url: HttpUrl, onElement: suspend (JsonElement) -> Unit) {
        withContext(Dispatchers.IO) {
            execute(url) { raw ->
                val input = BufferedInputStream(raw, 64 * 1024)
                when (firstSignificantChar(input)) {
                    '[' -> try {
                        for (el in json.decodeToSequence(input, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)) {
                            ensureActive()
                            onElement(el)
                        }
                    } catch (e: SerializationException) { throw ProviderError.UnexpectedResponse("liste JSON interrompue ou invalide") }
                    '{' -> {
                        val obj = try { json.decodeFromString(JsonElement.serializer(), input.bufferedReader().readText()) as JsonObject }
                        catch (e: Exception) { throw ProviderError.UnexpectedResponse("objet JSON invalide") }
                        if (obj["user_info"] != null) throw ProviderError.InvalidCredentials()
                        for (v in obj.values) { ensureActive(); onElement(v) }
                    }
                    null -> Unit // réponse vide = aucune entrée
                    else -> throw ProviderError.UnexpectedResponse("ce n'est pas du JSON")
                }
            }
        }
    }

    private fun firstSignificantChar(input: BufferedInputStream): Char? {
        while (true) {
            input.mark(4)
            val b = input.read()
            if (b == -1) return null
            val c = b.toChar()
            if (c.isWhitespace() || b == 0xEF || b == 0xBB || b == 0xBF) continue
            input.reset()
            return c
        }
    }

    private inline fun <T> execute(url: HttpUrl, read: (InputStream) -> T): T {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw ProviderError.HttpStatus(resp.code)
                val body = resp.body
                val type = body.contentType()?.subtype.orEmpty()
                if (type == "html") throw ProviderError.UnexpectedResponse("page HTML reçue")
                return read(body.byteStream())
            }
        } catch (e: ProviderError) {
            throw e
        } catch (e: CleartextNotAllowedException) {
            throw ProviderError.CleartextNotAllowed(e.host)
        } catch (e: CrossHostRedirectException) {
            throw ProviderError.RedirectRefused(e.to)
        } catch (e: SocketTimeoutException) {
            throw ProviderError.Timeout(e)
        } catch (e: UnknownHostException) {
            throw ProviderError.HostNotFound(e)
        } catch (e: ConnectException) {
            throw ProviderError.Unreachable(e)
        } catch (e: NoRouteToHostException) {
            throw ProviderError.Unreachable(e)
        } catch (e: SSLException) {
            throw ProviderError.Tls(e)
        } catch (e: IOException) {
            AppLog.w("Erreur réseau sur ${url}", e)
            throw ProviderError.Network(e)
        }
    }
}
