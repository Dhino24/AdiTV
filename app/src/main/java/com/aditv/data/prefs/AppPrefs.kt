package com.aditv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aditv_prefs")

/** Préférences non sensibles. Les secrets sont dans SecretStore, jamais ici. */
class AppPrefs(private val context: Context) {
    private val activeAccount = stringPreferencesKey("active_account_id")
    private val cleartextHosts = stringSetPreferencesKey("cleartext_hosts")

    val activeAccountId: Flow<String?> = context.dataStore.data.map { it[activeAccount] }
    suspend fun activeAccountIdNow(): String? = activeAccountId.first()
    suspend fun setActiveAccount(id: String?) = context.dataStore.edit { if (id == null) it.remove(activeAccount) else it[activeAccount] = id }

    suspend fun cleartextHostsNow(): Set<String> = context.dataStore.data.first()[cleartextHosts].orEmpty()
    suspend fun setCleartextHosts(hosts: Set<String>) = context.dataStore.edit { it[cleartextHosts] = hosts }

    // ---------- version de l'analyse du catalogue (resynchronisation après correctif) ----------
    private val parseVersion = intPreferencesKey("catalog_parse_version")
    suspend fun catalogParseVersion(): Int = context.dataStore.data.first()[parseVersion] ?: 0
    suspend fun setCatalogParseVersion(v: Int) = context.dataStore.edit { it[parseVersion] = v }

    // ---------- lecture : langue et qualité ----------
    private val languagePreset = stringPreferencesKey("language_preset")
    private val maxQuality = intPreferencesKey("max_quality")
    private val forceHighest = booleanPreferencesKey("force_highest_bitrate")

    /** Par défaut : version française d'abord, qualité maximale. */
    val variantPrefs: Flow<com.aditv.data.variants.VariantPrefs> = context.dataStore.data.map {
        com.aditv.data.variants.VariantPrefs(
            languages = com.aditv.data.variants.VariantPrefs.LANGUAGE_PRESETS[it[languagePreset] ?: "vf"] ?: com.aditv.data.variants.VariantPrefs().languages,
            maxQuality = it[maxQuality] ?: 4,
        )
    }
    val languagePresetKey: Flow<String> = context.dataStore.data.map { it[languagePreset] ?: "vf" }
    suspend fun setLanguagePreset(key: String) = context.dataStore.edit { it[languagePreset] = key }
    suspend fun setMaxQuality(q: Int) = context.dataStore.edit { it[maxQuality] = q }

    /** Flux adaptatifs (HLS multi-débits) : forcer le débit le plus élevé au lieu de l'adaptation au réseau. */
    val forceHighestBitrate: Flow<Boolean> = context.dataStore.data.map { it[forceHighest] ?: true }
    suspend fun setForceHighestBitrate(v: Boolean) = context.dataStore.edit { it[forceHighest] = v }

    // ---------- ouverture : image et son désactivables séparément ----------
    private val introImage = booleanPreferencesKey("intro_image")
    private val introSound = booleanPreferencesKey("intro_sound")
    val introImageEnabled: Flow<Boolean> = context.dataStore.data.map { it[introImage] ?: true }
    val introSoundEnabled: Flow<Boolean> = context.dataStore.data.map { it[introSound] ?: true }
    suspend fun setIntroImage(v: Boolean) = context.dataStore.edit { it[introImage] = v }
    suspend fun setIntroSound(v: Boolean) = context.dataStore.edit { it[introSound] = v }

    // ---------- sport : équipes suivies (premier lancement), délai des rappels ----------
    /** Dernier filtre choisi dans le Direct (catégorie, favoris, sport), par compte. */
    fun guideFilter(accountId: String): Flow<String?> = context.dataStore.data.map { it[stringPreferencesKey("guide_filter_$accountId")] }
    suspend fun setGuideFilter(accountId: String, key: String) = context.dataStore.edit { it[stringPreferencesKey("guide_filter_$accountId")] = key }

    private val reminderLead = intPreferencesKey("reminder_lead_minutes")
    val reminderLeadMinutes: Flow<Int> = context.dataStore.data.map { it[reminderLead] ?: 5 }
    suspend fun setReminderLead(minutes: Int) = context.dataStore.edit { it[reminderLead] = minutes }
    suspend fun sportSeeded(accountId: String): Boolean = context.dataStore.data.first()[booleanPreferencesKey("sport_seeded_$accountId")] ?: false
    suspend fun setSportSeeded(accountId: String) = context.dataStore.edit { it[booleanPreferencesKey("sport_seeded_$accountId")] = true }
    /** Tests : oublie les équipes déjà proposées d'office. */
    suspend fun clearSportSeeded() = context.dataStore.edit { p -> p.asMap().keys.filter { it.name.startsWith("sport_seeded_") }.forEach { p.remove(it) } }

    // ---------- accessibilité ----------
    private val textScale = intPreferencesKey("text_scale_percent")
    private val reduceMotion = booleanPreferencesKey("reduce_motion")
    /** Taille du texte : 100 (normale), 115 (grande), 130 (très grande). */
    val textScalePercent: Flow<Int> = context.dataStore.data.map { it[textScale] ?: 100 }
    suspend fun setTextScale(p: Int) = context.dataStore.edit { it[textScale] = p }
    /** Animations réduites dans ADITV (en plus du réglage système). */
    val reduceMotionPref: Flow<Boolean> = context.dataStore.data.map { it[reduceMotion] ?: false }
    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[reduceMotion] = v }

    /** Préférences exportables (aucun secret) : clé → valeur texte. */
    suspend fun exportable(): Map<String, String> = context.dataStore.data.first().asMap()
        .filterKeys { k -> k.name !in setOf("active_account_id", "cleartext_hosts", "catalog_parse_version") && !k.name.startsWith("sport_seeded_") }
        .mapKeys { it.key.name }.mapValues { it.value.toString() }

    suspend fun importValues(values: Map<String, String>) = context.dataStore.edit { p ->
        values["language_preset"]?.let { p[languagePreset] = it }
        values["max_quality"]?.toIntOrNull()?.let { p[maxQuality] = it }
        values["force_highest_bitrate"]?.toBooleanStrictOrNull()?.let { p[forceHighest] = it }
        values["intro_image"]?.toBooleanStrictOrNull()?.let { p[introImage] = it }
        values["intro_sound"]?.toBooleanStrictOrNull()?.let { p[introSound] = it }
        values["reminder_lead_minutes"]?.toIntOrNull()?.let { p[reminderLead] = it }
        values["text_scale_percent"]?.toIntOrNull()?.let { p[textScale] = it }
        values["reduce_motion"]?.toBooleanStrictOrNull()?.let { p[reduceMotion] = it }
    }
}
