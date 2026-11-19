package com.aditv.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aditv.core.AppLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProviderSecret(val username: String, val password: String) {
    override fun toString() = "ProviderSecret(***)"
}

/**
 * Identifiants chiffrés en AES-256-GCM avec une clé non exportable de l'Android Keystore.
 * Le texte chiffré est rangé dans un fichier de préférences privé, exclu des sauvegardes.
 * Si la clé devient inutilisable (réinitialisation, invalidation), les secrets sont effacés
 * et l'utilisateur doit se reconnecter. Ce n'est pas une protection inviolable du client.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("aditv_secrets", Context.MODE_PRIVATE)

    fun put(ref: String, secret: ProviderSecret) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(Json.encodeToString(ProviderSecret.serializer(), secret).toByteArray())
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString(ref, blob).commit()
    }

    /** Renvoie null si absent ou indéchiffrable (dans ce cas l'entrée est supprimée). */
    fun get(ref: String): ProviderSecret? {
        val blob = prefs.getString(ref, null) ?: return null
        return try {
            val (iv, ct) = blob.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            Json.decodeFromString(ProviderSecret.serializer(), String(cipher.doFinal(ct)))
        } catch (e: Exception) {
            AppLog.w("Secret illisible, suppression et reconnexion requise", e)
            remove(ref)
            null
        }
    }

    fun remove(ref: String) { prefs.edit().remove(ref).commit() }

    fun clearAll() { prefs.edit().clear().commit() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "aditv_provider_secrets_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
