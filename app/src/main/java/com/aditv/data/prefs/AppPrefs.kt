package com.aditv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
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
}
