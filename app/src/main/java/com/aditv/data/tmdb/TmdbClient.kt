package com.aditv.data.tmdb

import com.aditv.core.AppLog
import com.aditv.data.provider.JsonAccess.arr
import com.aditv.data.provider.JsonAccess.dbl
import com.aditv.data.provider.JsonAccess.integer
import com.aditv.data.provider.JsonAccess.obj
import com.aditv.data.provider.JsonAccess.text
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client TMDB v3. Clé personnelle de l'utilisateur : jeton d'accès v4 (« eyJ… », envoyé en Bearer)
 * ou clé v3 (paramètre api_key). Métadonnées uniquement : rien ici n'indique ce qui est lisible.
 */
class TmdbClient(
    private val client: OkHttpClient,
    private val key: suspend () -> String?,
    private val base: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun imageBaseUrl(): String =
        get("configuration")?.obj("images")?.text("secure_base_url") ?: DEFAULT_IMAGE_BASE

    suspend fun search(mediaType: String, query: String, year: Int?): List<TmdbCandidate> {
        val params = buildMap {
            put("query", query); put("language", "fr-FR"); put("include_adult", "false")
            if (year != null) put(if (mediaType == "movie") "primary_release_year" else "first_air_date_year", year.toString())
        }
        var results = get("search/$mediaType", params)?.arr("results").orEmpty()
        // L'année du fournisseur peut être fausse : on retente sans année plutôt que de conclure trop vite.
        if (results.isEmpty() && year != null) results = get("search/$mediaType", params - (if (mediaType == "movie") "primary_release_year" else "first_air_date_year"))?.arr("results").orEmpty()
        return results.mapNotNull { (it as? JsonObject)?.let { o -> candidate(mediaType, o) } }
    }

    suspend fun details(mediaType: String, id: Int): TmdbDetails {
        val append = if (mediaType == "movie") "credits,recommendations,similar" else "aggregate_credits,recommendations,similar"
        val o = get("$mediaType/$id", mapOf("language" to "fr-FR", "append_to_response" to append)) ?: throw TmdbError.NotFound()
        var overview = o.text("overview")
        // Repli : résumé anglais si aucun résumé français.
        val overviewLang = if (overview != null) "fr-FR" else {
            overview = get("$mediaType/$id", mapOf("language" to "en-US"))?.text("overview")
            if (overview != null) "en-US" else null
        }
        val castSrc = (o.obj("credits") ?: o.obj("aggregate_credits"))?.arr("cast").orEmpty().take(12)
        val cast = castSrc.mapNotNull { c ->
            val co = c as? JsonObject ?: return@mapNotNull null
            val name = co.text("name") ?: return@mapNotNull null
            val role = co.text("character") ?: co.arr("roles")?.firstOrNull()?.let { (it as? JsonObject)?.text("character") }
            name to role
        }
        val crew = if (mediaType == "movie")
            o.obj("credits")?.arr("crew").orEmpty().mapNotNull { (it as? JsonObject)?.takeIf { c -> c.text("job") == "Director" }?.text("name") }.distinct()
        else o.arr("created_by").orEmpty().mapNotNull { (it as? JsonObject)?.text("name") }
        val related = (o.obj("recommendations")?.arr("results").orEmpty() + o.obj("similar")?.arr("results").orEmpty())
            .mapNotNull { (it as? JsonObject)?.integer("id") }.distinct().take(40)
        return TmdbDetails(
            candidate = candidate(mediaType, o) ?: throw TmdbError.Parse(),
            overview = overview,
            overviewLanguage = overviewLang,
            backdropPath = o.text("backdrop_path"),
            releaseDate = o.text("release_date", "first_air_date"),
            runtimeMin = o.integer("runtime") ?: o.arr("episode_run_time")?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() },
            genres = o.arr("genres").orEmpty().mapNotNull { (it as? JsonObject)?.text("name") },
            voteAverage = o.dbl("vote_average"),
            voteCount = o.integer("vote_count"),
            cast = cast,
            crew = crew,
            seasonCount = o.integer("number_of_seasons"),
            relatedIds = related,
        )
    }

    private fun candidate(mediaType: String, o: JsonObject): TmdbCandidate? {
        val id = o.integer("id") ?: return null
        val title = o.text("title", "name") ?: return null
        return TmdbCandidate(
            id = id, mediaType = mediaType, title = title, originalTitle = o.text("original_title", "original_name"),
            year = o.text("release_date", "first_air_date")?.take(4)?.toIntOrNull(),
            popularity = o.dbl("popularity") ?: 0.0, posterPath = o.text("poster_path"),
        )
    }

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        val k = key()?.trim()?.takeIf { it.isNotEmpty() } ?: throw TmdbError.NoKey()
        val bearer = k.startsWith("eyJ")
        val url = base.newBuilder().addPathSegments(path).apply {
            params.forEach { (n, v) -> addQueryParameter(n, v) }
            if (!bearer) addQueryParameter("api_key", k)
        }.build()
        val req = Request.Builder().url(url).header("Accept", "application/json")
            .apply { if (bearer) header("Authorization", "Bearer $k") }.build()
        repeat(3) { attempt ->
            val outcome = withContext(Dispatchers.IO) {
                try {
                    client.newCall(req).execute().use { r ->
                        when {
                            r.isSuccessful -> Outcome.Ok(json.decodeFromString(JsonElement.serializer(), r.body.string()) as? JsonObject)
                            r.code == 401 -> throw TmdbError.InvalidKey()
                            r.code == 404 -> Outcome.Ok(null)
                            r.code == 429 -> Outcome.Retry(((r.header("Retry-After")?.toLongOrNull() ?: 2L) * 1000).coerceIn(500, 10_000), TmdbError.RateLimited())
                            r.code >= 500 -> Outcome.Retry(1_000L * (attempt + 1), TmdbError.Server(r.code))
                            else -> throw TmdbError.Server(r.code)
                        }
                    }
                } catch (e: TmdbError) { throw e }
                catch (e: IOException) { Outcome.Retry(1_000L * (attempt + 1), TmdbError.Network(e)) }
                catch (e: Exception) { throw TmdbError.Parse() }
            }
            when (outcome) {
                is Outcome.Ok -> return outcome.body
                is Outcome.Retry -> {
                    if (attempt == 2) throw outcome.error
                    AppLog.d("TMDB : nouvel essai dans ${outcome.delayMs} ms (${outcome.error.message})")
                    delay(outcome.delayMs)
                }
            }
        }
        error("inaccessible")
    }

    private sealed interface Outcome {
        data class Ok(val body: JsonObject?) : Outcome
        data class Retry(val delayMs: Long, val error: TmdbError) : Outcome
    }

    companion object {
        const val DEFAULT_IMAGE_BASE = "https://image.tmdb.org/t/p/"
        const val ATTRIBUTION = "This product uses the TMDB API but is not endorsed or certified by TMDB."
    }
}
