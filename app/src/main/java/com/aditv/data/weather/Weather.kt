package com.aditv.data.weather

import com.aditv.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Météo du moment pour l'écran de veille. */
data class WeatherNow(val city: String, val tempC: Int, val minC: Int?, val maxC: Int?, val label: String, val icon: String)

/**
 * Météo via Open-Meteo (gratuit, sans clé ni compte). Seul le nom de la ville choisie dans les Réglages est envoyé
 * (recherche de la ville), puis ses coordonnées. Résultat gardé 30 min.
 */
class WeatherRepository(private val http: OkHttpClient, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cached: Triple<String, Long, WeatherNow>? = null
    private var place: Triple<String, Double, Double>? = null
    private var placeQuery: String? = null

    suspend fun now(city: String): WeatherNow? = withContext(Dispatchers.IO) {
        val q = city.trim()
        if (q.isEmpty()) return@withContext null
        cached?.takeIf { it.first.equals(q, true) && clock() - it.second < 30 * 60_000L }?.let { return@withContext it.third }
        runCatching {
            val p = place?.takeIf { placeQuery.equals(q, true) } ?: geocode(q)?.also { place = it; placeQuery = q } ?: return@runCatching null
            val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", p.second.toString()).addQueryParameter("longitude", p.third.toString())
                .addQueryParameter("current", "temperature_2m,weather_code,is_day")
                .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min")
                .addQueryParameter("timezone", "auto").addQueryParameter("forecast_days", "1").build()
            val o = get(url.toString()) ?: return@runCatching null
            val cur = o["current"] as? JsonObject ?: return@runCatching null
            val code = (cur["weather_code"] as? JsonPrimitive)?.intOrNull ?: 0
            val day = (cur["is_day"] as? JsonPrimitive)?.intOrNull != 0
            val daily = o["daily"] as? JsonObject
            fun first(k: String) = ((daily?.get(k) as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.doubleOrNull?.let { Math.round(it).toInt() }
            val (label, icon) = describe(code, day)
            WeatherNow(p.first, Math.round((cur["temperature_2m"] as? JsonPrimitive)?.doubleOrNull ?: return@runCatching null).toInt(),
                first("temperature_2m_min"), first("temperature_2m_max"), label, icon)
        }.onFailure { AppLog.w("Météo indisponible") }.getOrNull()?.also { cached = Triple(q, clock(), it) }
    }

    private fun geocode(q: String): Triple<String, Double, Double>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", q).addQueryParameter("count", "1").addQueryParameter("language", "fr").build()
        val r = ((get(url.toString())?.get("results") as? JsonArray)?.firstOrNull() as? JsonObject) ?: return null
        val name = (r["name"] as? JsonPrimitive)?.content ?: q
        val lat = (r["latitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
        val lon = (r["longitude"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return Triple(name, lat, lon)
    }

    private fun get(url: String): JsonObject? = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) null else json.parseToJsonElement(r.body.string()) as? JsonObject
    }

    companion object {
        /** Codes météo WMO → libellé et pictogramme. */
        fun describe(code: Int, day: Boolean = true): Pair<String, String> = when (code) {
            0 -> "Ciel dégagé" to if (day) "☀️" else "🌙"
            1, 2 -> "Éclaircies" to if (day) "🌤️" else "☁️"
            3 -> "Couvert" to "☁️"
            45, 48 -> "Brouillard" to "🌫️"
            in 51..57 -> "Bruine" to "🌦️"
            in 61..67, in 80..82 -> "Pluie" to "🌧️"
            in 71..77, 85, 86 -> "Neige" to "🌨️"
            in 95..99 -> "Orage" to "⛈️"
            else -> "—" to "🌡️"
        }
    }
}
