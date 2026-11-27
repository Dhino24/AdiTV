package com.aditv.playback.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Choix de moteur mémorisé par contenu (clé de variante ou identifiant), et journal des dernières sessions
 * pour comparer les moteurs dans les mêmes conditions (Réglages → Diagnostic de lecture). Aucune URL stockée.
 */
class EngineStore(context: Context) {
    private val sp = context.getSharedPreferences("aditv_engines", Context.MODE_PRIVATE)

    fun choiceFor(key: String?): EngineChoice? = key?.let { sp.getString("c:$it", null) }?.let { runCatching { EngineChoice.valueOf(it) }.getOrNull() }
    fun setChoice(key: String?, choice: EngineChoice) {
        if (key == null) return
        sp.edit().apply { if (choice == EngineChoice.AUTO) remove("c:$key") else putString("c:$key", choice.name) }.apply()
    }
    /** Décalage du son mémorisé par chaîne / contenu (ms, 0 par défaut). */
    fun audioDelayFor(key: String?): Int = key?.let { sp.getInt("d:$it", 0) } ?: 0
    fun setAudioDelay(key: String?, ms: Int) {
        if (key == null) return
        sp.edit().apply { if (ms == 0) remove("d:$key") else putInt("d:$key", ms) }.apply()
    }
    /** Générique d'une série (début, fin en ms), commun à tous ses épisodes. */
    fun introFor(seriesId: String): Pair<Long, Long>? = sp.getString("i:$seriesId", null)?.split(',')?.mapNotNull { it.toLongOrNull() }
        ?.takeIf { it.size == 2 && it[1] > it[0] }?.let { it[0] to it[1] }
    fun setIntro(seriesId: String, start: Long?, end: Long?) {
        sp.edit().apply { if (start == null || end == null) remove("i:$seriesId") else putString("i:$seriesId", "$start,$end") }.apply()
    }
    var defaultChoice: EngineChoice
        get() = sp.getString("default", null)?.let { runCatching { EngineChoice.valueOf(it) }.getOrNull() } ?: EngineChoice.AUTO
        set(v) { sp.edit().putString("default", v.name).apply() }
    var turboDefault: Boolean
        get() = sp.getBoolean("turbo", false)
        set(v) { sp.edit().putBoolean("turbo", v).apply() }

    data class SessionLog(
        val at: Long, val title: String, val engine: String, val turbo: Boolean, val startupMs: Long?, val stalls: Int,
        val dropped: Int?, val watchedSec: Long, val error: String?, val fallback: Boolean,
    )

    fun record(s: SessionLog) {
        val arr = runCatching { JSONArray(sp.getString("log", "[]")) }.getOrDefault(JSONArray())
        val out = JSONArray().put(JSONObject().apply {
            put("at", s.at); put("title", s.title); put("engine", s.engine); put("turbo", s.turbo); put("startup", s.startupMs ?: -1)
            put("stalls", s.stalls); put("dropped", s.dropped ?: -1); put("watched", s.watchedSec); put("error", s.error ?: ""); put("fallback", s.fallback)
        })
        for (i in 0 until minOf(arr.length(), 19)) out.put(arr.get(i))
        sp.edit().putString("log", out.toString()).apply()
    }

    fun sessions(): List<SessionLog> {
        val arr = runCatching { JSONArray(sp.getString("log", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SessionLog(o.getLong("at"), o.getString("title"), o.getString("engine"), o.optBoolean("turbo"), o.getLong("startup").takeIf { it >= 0 },
                o.getInt("stalls"), o.getInt("dropped").takeIf { it >= 0 }, o.getLong("watched"), o.getString("error").ifEmpty { null }, o.optBoolean("fallback"))
        }
    }

    fun clearLog() = sp.edit().remove("log").apply()
}
