package com.aditv.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Normalise l'adresse serveur saisie : schéma explicite, hôte en minuscules, sans script ni paramètres. */
object Endpoint {
    sealed interface Result {
        data class Ok(val url: HttpUrl, val schemeWasAssumed: Boolean) : Result
        data class Invalid(val reason: String) : Result
    }

    private val knownScripts = listOf("player_api.php", "get.php", "xmltv.php", "panel_api.php")

    fun canonicalize(input: String): Result {
        val raw = input.trim()
        if (raw.isEmpty()) return Result.Invalid("Adresse du serveur vide.")
        val hasScheme = raw.startsWith("http://", true) || raw.startsWith("https://", true)
        if (!hasScheme && raw.contains("://")) return Result.Invalid("Seuls http:// et https:// sont acceptés.")
        val withScheme = if (hasScheme) raw else "https://$raw"
        val parsed = withScheme.toHttpUrlOrNull() ?: return Result.Invalid("Adresse du serveur invalide.")
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return Result.Invalid("Ne mettez pas l'identifiant ni le mot de passe dans l'adresse.")
        }
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.lastOrNull()?.lowercase() in knownScripts) segments.removeAt(segments.lastIndex)
        val b = HttpUrl.Builder().scheme(parsed.scheme).host(parsed.host.lowercase()).port(parsed.port)
        segments.forEach { b.addPathSegment(it) }
        return Result.Ok(b.build(), schemeWasAssumed = !hasScheme)
    }

    /** Forme stable pour l'affichage et la comparaison, sans barre finale. */
    fun display(url: HttpUrl): String = url.toString().trimEnd('/')
}
