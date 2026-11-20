package com.aditv.data.prefs

import android.content.Context
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.FavoriteChannelEntity
import com.aditv.data.sport.SportMatcher
import com.aditv.data.sport.SportRepository
import com.aditv.playback.engine.EngineChoice
import com.aditv.playback.engine.EngineStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export / import des préférences dans un fichier JSON du stockage de l'application
 * (`Android/data/com.aditv.app/files/aditv-preferences.json`, récupérable par adb).
 * Jamais de secret : ni accès fournisseur, ni mot de passe, ni clé TMDB. Favoris et Ma liste sont
 * exportés par titre fournisseur, pour être retrouvés après une réinstallation.
 */
class PrefsBackup(
    private val context: Context,
    private val db: AditvDatabase,
    private val prefs: AppPrefs,
    private val engines: EngineStore,
    private val sport: SportRepository,
) {
    val file: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "aditv-preferences.json")

    suspend fun export(accountId: String): File = withContext(Dispatchers.IO) {
        val o = JSONObject()
        o.put("format", "aditv-preferences/1")
        o.put("preferences", JSONObject(prefs.exportable()))
        o.put("engineDefault", engines.defaultChoice.name)
        o.put("turboDefault", engines.turboDefault)
        o.put("follows", JSONArray(db.sport().follows(accountId).map { it.key }))
        val favIds = db.live().favoriteIds(accountId).first().toSet()
        val favTitles = db.catalog().allChannels(accountId).filter { it.id in favIds }.mapNotNull { db.catalog().observeItem(it.id).first()?.rawTitle }
        o.put("favoriteChannels", JSONArray(favTitles))
        o.put("myList", JSONArray(db.library().watchlist(accountId).first().mapNotNull { c -> db.catalog().observeItem(c.id).first()?.let { "${it.type}|${it.rawTitle}" } }))
        file.writeText(o.toString(2))
        file
    }

    /** Renvoie un résumé de ce qui a été importé. */
    suspend fun import(accountId: String): String = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext "Aucun fichier à importer (${file.path})."
        val o = runCatching { JSONObject(file.readText()) }.getOrElse { return@withContext "Fichier illisible." }
        if (o.optString("format") != "aditv-preferences/1") return@withContext "Format inconnu."
        o.optJSONObject("preferences")?.let { p -> prefs.importValues(p.keys().asSequence().associateWith { k -> p.getString(k) }) }
        o.optString("engineDefault").takeIf { it.isNotEmpty() }?.let { runCatching { engines.defaultChoice = EngineChoice.valueOf(it) } }
        if (o.has("turboDefault")) engines.turboDefault = o.getBoolean("turboDefault")
        var follows = 0
        o.optJSONArray("follows")?.let { a ->
            for (i in 0 until a.length()) SportMatcher.PRESETS.firstOrNull { it.key == a.getString(i) }?.let { sport.follow(accountId, it); follows++ }
        }
        val channels = db.catalog().allChannels(accountId)
        var favs = 0
        o.optJSONArray("favoriteChannels")?.let { a ->
            val wanted = (0 until a.length()).map { a.getString(it) }.toSet()
            channels.forEach { c ->
                val raw = db.catalog().observeItem(c.id).first()?.rawTitle
                if (raw in wanted) { db.live().addFavorite(FavoriteChannelEntity(accountId, c.id, System.currentTimeMillis())); favs++ }
            }
        }
        var list = 0
        o.optJSONArray("myList")?.let { a ->
            for (i in 0 until a.length()) {
                val (type, raw) = a.getString(i).split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                val key = com.aditv.core.TitleNormalizer.clean(raw).searchKey
                db.catalog().search(accountId, key, 20).firstOrNull { it.type == type }?.let {
                    db.library().addToWatchlist(com.aditv.data.db.WatchlistEntity(accountId, it.id, System.currentTimeMillis())); list++
                }
            }
        }
        "Importé : préférences, $follows équipe(s) suivie(s), $favs chaîne(s) favorite(s), $list titre(s) de Ma liste."
    }
}
