package com.aditv.data.profile

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Profil de la maison : sa liste, sa progression, ses favoris et sa dernière chaîne. [color] : index de la palette d'avatars. */
data class Profile(val id: String, val name: String, val color: Int)

/**
 * Profils (sur l'appareil, sans aucun secret). Ma liste, la progression et les chaînes favorites sont rangées sous une
 * « clé de bibliothèque » : le compte pour le profil principal (données d'avant les profils conservées telles quelles),
 * « compte~id » pour les autres. Le catalogue, lui, reste commun.
 */
object Profiles {
    const val MAIN = "main"
    private var prefs: SharedPreferences? = null
    private val _list = MutableStateFlow(listOf(Profile(MAIN, "Moi", 0)))
    val list: StateFlow<List<Profile>> = _list
    private val _active = MutableStateFlow(MAIN)
    val active: StateFlow<String> = _active

    fun init(context: Context) {
        val p = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
        prefs = p
        _list.value = parse(p.getString("list", null)) ?: listOf(Profile(MAIN, "Moi", 0))
        _active.value = p.getString("active", MAIN)?.takeIf { id -> _list.value.any { it.id == id } } ?: MAIN
    }

    /** Clé des données personnelles (liste, progression, favoris) pour ce compte et le profil actif. */
    fun key(accountId: String): String = if (_active.value == MAIN) accountId else "$accountId~${_active.value}"

    fun current(): Profile = _list.value.firstOrNull { it.id == _active.value } ?: _list.value.first()

    fun select(id: String) { if (_list.value.any { it.id == id }) { _active.value = id; save() } }

    fun add(name: String): Profile {
        val p = Profile("p" + System.currentTimeMillis().toString(36), name.trim().ifEmpty { "Profil" }.take(20), (_list.value.maxOfOrNull { it.color } ?: 0) + 1)
        _list.value = _list.value + p; save(); return p
    }

    fun rename(id: String, name: String) { _list.value = _list.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }.take(20)) else it }; save() }

    /** Le profil principal ne se supprime pas (il porte les données d'avant les profils). */
    fun remove(id: String) {
        if (id == MAIN) return
        _list.value = _list.value.filterNot { it.id == id }
        if (_active.value == id) _active.value = MAIN
        save()
    }

    private fun save() {
        val arr = JSONArray(_list.value.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color) })
        prefs?.edit()?.putString("list", arr.toString())?.putString("active", _active.value)?.apply()
    }

    private fun parse(s: String?): List<Profile>? = runCatching {
        val a = JSONArray(s ?: return null)
        (0 until a.length()).map { a.getJSONObject(it) }.map { Profile(it.getString("id"), it.getString("name"), it.optInt("color")) }
            .takeIf { l -> l.any { it.id == MAIN } }
    }.getOrNull()
}
