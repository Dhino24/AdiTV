package com.aditv.data.tmdb

import com.aditv.core.Redactor
import com.aditv.security.ProviderSecret
import com.aditv.security.SecretStore

/** Clé TMDB personnelle, chiffrée comme les accès fournisseur. Jamais embarquée dans l'APK. */
class TmdbKeyStore(private val secrets: SecretStore) {
    @Volatile private var cached: String? = null
    @Volatile private var loaded = false

    fun get(): String? {
        if (!loaded) { cached = secrets.get(REF)?.password; loaded = true; Redactor.registerSecret(cached) }
        return cached
    }

    fun set(key: String) {
        val k = key.trim()
        secrets.put(REF, ProviderSecret("tmdb", k)); cached = k; loaded = true; Redactor.registerSecret(k)
    }

    fun clear() { secrets.remove(REF); cached = null; loaded = true }

    private companion object { const val REF = "tmdb:key" }
}
