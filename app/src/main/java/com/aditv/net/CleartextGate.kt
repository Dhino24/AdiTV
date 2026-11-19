package com.aditv.net

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Interceptor
import okhttp3.Response

class CleartextNotAllowedException(val host: String) :
    IOException("HTTP en clair refusé vers $host : aucun consentement explicite pour cet hôte.")

class CrossHostRedirectException(val from: String, val to: String) :
    IOException("Redirection de $from vers un autre hôte ($to) refusée pour une requête contenant des accès.")

/**
 * Intercepteur réseau (appelé à chaque saut, redirections comprises) :
 * - HTTPS toujours autorisé ;
 * - HTTP uniquement vers les hôtes que l'utilisateur a explicitement acceptés ;
 * - optionnellement, refuse qu'une redirection emmène la requête vers un autre hôte (requêtes API avec identifiants).
 */
class CleartextGate(private val blockCrossHostRedirects: Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val hop = chain.request().url
        val original = chain.call().request().url
        if (blockCrossHostRedirects && hop.host != original.host) {
            throw CrossHostRedirectException(original.host, hop.host)
        }
        if (!hop.isHttps && hop.host !in allowedCleartextHosts.get()) {
            throw CleartextNotAllowedException(hop.host)
        }
        return chain.proceed(chain.request())
    }

    companion object {
        private val allowedCleartextHosts = AtomicReference<Set<String>>(emptySet())
        fun setAllowedHosts(hosts: Set<String>) = allowedCleartextHosts.set(hosts.map { it.lowercase() }.toSet())
        fun allowedHosts(): Set<String> = allowedCleartextHosts.get()
    }
}

/**
 * Redirections suivies manuellement (le client doit avoir followRedirects(false)) :
 * uniquement vers le même hôte, 5 sauts au plus. Évite toute connexion vers un hôte tiers
 * avec une URL contenant des identifiants.
 */
class SameHostRedirects(private val maxHops: Int = 5) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var hops = 0
        while (response.isRedirect) {
            val location = response.header("Location") ?: return response
            val next = request.url.resolve(location) ?: return response
            if (next.host != request.url.host) {
                response.close()
                throw CrossHostRedirectException(request.url.host, next.host)
            }
            if (++hops > maxHops) { response.close(); throw IOException("Trop de redirections.") }
            response.close()
            request = request.newBuilder().url(next).build()
            response = chain.proceed(request)
        }
        return response
    }
}
