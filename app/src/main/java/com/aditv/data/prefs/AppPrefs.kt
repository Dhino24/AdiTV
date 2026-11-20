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
}
