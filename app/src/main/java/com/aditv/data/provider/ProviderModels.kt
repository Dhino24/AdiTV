package com.aditv.data.provider

import okhttp3.HttpUrl

enum class MediaType { LIVE, MOVIE, SERIES }

enum class ProviderKind { XTREAM, DEMO }

class ProviderCredentials(val endpoint: HttpUrl, val username: String, val password: String) {
    override fun toString() = "ProviderCredentials(endpoint=${endpoint.host}, ***)"
}

/** Statut tel que déclaré par le fournisseur. Champs null = non exposé : ne rien supposer. */
data class AccountStatus(
    val status: String?,
    val expiresAtEpochSec: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean?,
    val serverTimezone: String?,
    val apiShape: String,
    /** Formats de flux direct autorisés (ex. ts, m3u8), si communiqués. */
    val liveFormats: List<String>? = null,
)

data class RawCategory(val providerId: String, val name: String, val position: Int)

data class RawItem(
    val providerId: String,
    val type: MediaType,
    val rawTitle: String,
    val position: Int?,
    val categoryProviderId: String?,
    val imageUrl: String?,
    val providerTmdbId: String?,
    val providerRating: String?,
    val addedAtEpochSec: Long?,
    val containerExt: String?,
    val epgChannelId: String?,
    val catchupDays: Int?,
    val plot: String?,
    val genre: String?,
    val releaseYear: Int?,
)

data class RawEpisode(
    val providerId: String,
    val season: Int,
    val number: Int?,
    val title: String,
    val plot: String?,
    val imageUrl: String?,
    val durationSec: Int?,
    val containerExt: String?,
    val airDate: String?,
)

/** Ce que l'on veut lire. L'URL est construite au moment de la lecture et jamais enregistrée. */
sealed interface StreamTarget {
    data class Movie(val providerId: String, val containerExt: String?) : StreamTarget
    data class Episode(val providerId: String, val containerExt: String?) : StreamTarget
    data class Live(val providerId: String) : StreamTarget
}

/** Erreurs typées : chaque cas a un message utile, sans prétendre identifier une cause invisible. */
sealed class ProviderError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidCredentials : ProviderError("Identifiant ou mot de passe refusé par le serveur.")
    class AccountInactive(val status: String) : ProviderError("Le serveur indique un compte « $status ».")
    class HttpStatus(val code: Int) : ProviderError(
        when (code) {
            401, 403 -> "Accès refusé par le serveur (HTTP $code). Vérifiez vos accès ou l'adresse."
            404 -> "Adresse introuvable sur ce serveur (HTTP 404). Vérifiez l'URL fournie par votre fournisseur."
            429 -> "Le serveur limite les requêtes (HTTP 429). Réessayez plus tard."
            in 500..599 -> "Le serveur a renvoyé une erreur (HTTP $code)."
            else -> "Réponse inattendue du serveur (HTTP $code)."
        }
    )
    class Timeout(cause: Throwable) : ProviderError("Pas de réponse dans le délai imparti. Le serveur ou le réseau peut être lent ou indisponible.", cause)
    class HostNotFound(cause: Throwable) : ProviderError("Nom de serveur introuvable. Vérifiez l'adresse et la connexion Internet.", cause)
    class Unreachable(cause: Throwable) : ProviderError("Connexion au serveur impossible.", cause)
    class Tls(cause: Throwable) : ProviderError("Connexion sécurisée (HTTPS) impossible avec ce serveur. Si votre fournisseur n'utilise que HTTP, saisissez http:// explicitement.", cause)
    class CleartextNotAllowed(val host: String) : ProviderError("Connexion HTTP en clair vers $host non autorisée. Confirmez d'abord le choix HTTP.")
    class RedirectRefused(val to: String) : ProviderError("Le serveur redirige vers un autre hôte ($to) : refusé pour protéger vos accès.")
    class UnexpectedResponse(detail: String) : ProviderError("La réponse ne correspond pas à une API compatible Xtream ($detail).")
    class Network(cause: Throwable) : ProviderError("Erreur réseau : ${cause.javaClass.simpleName}.", cause)
}
