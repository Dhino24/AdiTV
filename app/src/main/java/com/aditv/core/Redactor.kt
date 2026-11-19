package com.aditv.core

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Masque les secrets avant tout affichage ou journal : paramètres sensibles des URLs,
 * segments d'identifiants des chemins Xtream et valeurs secrètes enregistrées à la connexion.
 */
object Redactor {
    private const val MASK = "***"
    private val secrets = CopyOnWriteArraySet<String>()

    private val sensitiveQuery = Regex(
        "([?&](?:username|user|password|pass|pwd|token|key|api_key|apikey|auth)=)[^&#\\s]*",
        RegexOption.IGNORE_CASE,
    )
    // Chemins Xtream : /live/<user>/<pass>/..., /movie/..., /series/..., /timeshift/<user>/<pass>/...
    private val xtreamPath = Regex("(/(?:live|movie|series|timeshift)/)[^/\\s]+/[^/\\s]+/", RegexOption.IGNORE_CASE)
    private val userInfo = Regex("(://)[^/@\\s]+@")

    /** Enregistre une valeur à masquer partout (mot de passe, identifiant). Ignore les valeurs trop courtes. */
    fun registerSecret(value: String?) {
        if (value != null && value.length >= 3) secrets += value
    }

    fun clearSecrets() = secrets.clear()

    fun redact(input: String?): String {
        if (input.isNullOrEmpty()) return input.orEmpty()
        var out: String = input
        out = sensitiveQuery.replace(out) { it.groupValues[1] + MASK }
        out = xtreamPath.replace(out) { it.groupValues[1] + "$MASK/$MASK/" }
        out = userInfo.replace(out) { it.groupValues[1] + "$MASK@" }
        for (s in secrets.sortedByDescending { it.length }) out = out.replace(s, MASK)
        return out
    }
}
