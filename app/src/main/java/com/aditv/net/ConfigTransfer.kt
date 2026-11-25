package com.aditv.net

import android.util.Base64
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Copie de la configuration d'une TV de la maison vers une autre (abonnement + clés TMDB, API-Football,
 * football-data.org). La TV qui REÇOIT ouvre sa petite page locale (écran de connexion → « Recevoir d'une autre TV »)
 * et affiche son adresse et un code à 4 chiffres ; la TV qui ENVOIE (Réglages) les saisit et envoie.
 * Sécurité : adresse du réseau local uniquement (jamais Internet), code à 4 chiffres + jeton, usage unique, 5 essais,
 * page fermée au bout de 10 min. Transfert en HTTP simple sur le Wi-Fi de la maison (dit à l'écran). À réserver à vos TV.
 */
object ConfigTransfer {
    data class Payload(
        val server: String?, val username: String?, val password: String?,
        val tmdb: String?, val football: String?, val footballData: String?,
    )

    fun encode(p: Payload): String {
        val o = JSONObject().put("v", 1)
        fun put(k: String, v: String?) { if (!v.isNullOrBlank()) o.put(k, v) }
        put("server", p.server); put("username", p.username); put("password", p.password)
        put("tmdb", p.tmdb); put("football", p.football); put("footballData", p.footballData)
        return Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP)
    }

    fun decode(s: String): Payload? = runCatching {
        val o = JSONObject(String(Base64.decode(s.trim(), Base64.DEFAULT)))
        fun get(k: String) = o.optString(k).takeIf { it.isNotBlank() }
        Payload(get("server"), get("username"), get("password"), get("tmdb"), get("football"), get("footballData"))
    }.getOrNull()

    /** « 192.168.1.20:41234 » ou « http://192.168.1.20:41234/ » → URL, seulement vers une adresse du réseau local. */
    fun target(input: String): okhttp3.HttpUrl? {
        val raw = input.trim().removeSuffix("/")
        val url = (if (raw.startsWith("http://")) raw else "http://$raw").toHttpUrlOrNull() ?: return null
        val ip = runCatching { InetAddress.getByName(url.host) }.getOrNull() ?: return null
        return url.takeIf { ip.isSiteLocalAddress && url.host.all { it.isDigit() || it == '.' } }
    }

    private val lan = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()

    /** Envoie la configuration à l'autre TV ; renvoie un message à afficher. */
    suspend fun send(address: String, pin: String, payload: Payload): String = withContext(Dispatchers.IO) {
        val base = target(address) ?: return@withContext "Adresse invalide : tapez celle affichée sur l'autre TV (ex. 192.168.1.20:41234)."
        val page = lan.newCall(Request.Builder().url(base.newBuilder().addQueryParameter("pin", pin.trim()).build()).build()).execute().use { it.body.string() }
        val token = Regex("""name="t" value="([0-9a-f]{32})"""").find(page)?.groupValues?.get(1)
            ?: return@withContext "Code refusé par l'autre TV (vérifiez les 4 chiffres ; 5 essais au plus)."
        val body = FormBody.Builder().add("t", token).add("payload", encode(payload)).build()
        lan.newCall(Request.Builder().url(base).post(body).build()).execute().use { r ->
            if (r.isSuccessful) "Configuration envoyée : terminez sur l'autre TV (« Se connecter »)." else "L'autre TV a refusé l'envoi (HTTP ${r.code})."
        }
    }
}
