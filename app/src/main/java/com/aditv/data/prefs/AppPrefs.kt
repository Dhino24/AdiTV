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
    /** Adapter la fréquence de l'écran à la cadence du flux (50 Hz pour le foot, 24 Hz pour les films). */
    private val matchRate = booleanPreferencesKey("match_frame_rate")
    val matchFrameRate: Flow<Boolean> = context.dataStore.data.map { it[matchRate] ?: true }
    suspend fun setMatchFrameRate(v: Boolean) = context.dataStore.edit { it[matchRate] = v }

    // ---------- ouverture : image et son désactivables séparément ----------
    private val introImage = booleanPreferencesKey("intro_image")
    private val introSound = booleanPreferencesKey("intro_sound")
    val introImageEnabled: Flow<Boolean> = context.dataStore.data.map { it[introImage] ?: true }
    val introSoundEnabled: Flow<Boolean> = context.dataStore.data.map { it[introSound] ?: true }
    suspend fun setIntroImage(v: Boolean) = context.dataStore.edit { it[introImage] = v }
    suspend fun setIntroSound(v: Boolean) = context.dataStore.edit { it[introSound] = v }
    /** Petit son doux quand la sélection se déplace (façon Apple TV). */
    private val navSoundKey = booleanPreferencesKey("nav_sound")
    val navSoundEnabled: Flow<Boolean> = context.dataStore.data.map { it[navSoundKey] ?: true }
    suspend fun setNavSound(v: Boolean) = context.dataStore.edit { it[navSoundKey] = v }

    // ---------- sport : équipes suivies (premier lancement), délai des rappels ----------
    /** « Plus tard » sur la proposition de mise à jour des films et séries : pas de nouvelle proposition avant cette date. */
    private val vodSnooze = androidx.datastore.preferences.core.longPreferencesKey("vod_update_snooze_until")
    val vodUpdateSnoozedUntil: Flow<Long> = context.dataStore.data.map { it[vodSnooze] ?: 0L }
    suspend fun snoozeVodUpdate(until: Long) = context.dataStore.edit { it[vodSnooze] = until }

    /** Chaînes déplacées à la main dans une autre famille du Direct (identifiant de chaîne → famille). */
    private val familyOverrides = androidx.datastore.preferences.core.stringSetPreferencesKey("channel_family_overrides")
    val channelFamilyOverrides: Flow<Map<String, String>> = context.dataStore.data.map { p ->
        p[familyOverrides].orEmpty().mapNotNull { e -> e.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }
    suspend fun setChannelFamily(itemId: String, family: String?) = context.dataStore.edit { p ->
        val rest = p[familyOverrides].orEmpty().filterNot { it.startsWith(itemId + "\t") }.toSet()
        p[familyOverrides] = if (family == null) rest else rest + (itemId + "\t" + family)
    }

    /** Dernier filtre choisi dans le Direct (catégorie, favoris, sport), par compte. */
    // Démarrage sur la dernière chaîne (option) ; dernière chaîne regardée par profil (« id|liste de zapping »).
    private val startOnLast = booleanPreferencesKey("start_on_last_channel")
    val startOnLastChannel: Flow<Boolean> = context.dataStore.data.map { it[startOnLast] ?: false }
    suspend fun setStartOnLastChannel(v: Boolean) = context.dataStore.edit { it[startOnLast] = v }
    fun lastLive(profileKey: String): Flow<String?> = context.dataStore.data.map { it[stringPreferencesKey("last_live_$profileKey")] }
    suspend fun setLastLive(profileKey: String, itemId: String, list: String?) =
        context.dataStore.edit { it[stringPreferencesKey("last_live_$profileKey")] = itemId + "|" + (list ?: "all") }

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
        values["nav_sound"]?.toBooleanStrictOrNull()?.let { p[navSoundKey] = it }
        values["reminder_lead_minutes"]?.toIntOrNull()?.let { p[reminderLead] = it }
        values["text_scale_percent"]?.toIntOrNull()?.let { p[textScale] = it }
        values["reduce_motion"]?.toBooleanStrictOrNull()?.let { p[reduceMotion] = it }
    }
}
