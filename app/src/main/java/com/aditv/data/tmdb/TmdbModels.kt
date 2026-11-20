package com.aditv.data.tmdb

import kotlinx.serialization.Serializable

@Serializable
data class TmdbCandidate(
    val id: Int,
    /** movie | tv */
    val mediaType: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val popularity: Double = 0.0,
    val posterPath: String? = null,
    val backdropPath: String? = null,
)

data class TmdbDetails(
    val candidate: TmdbCandidate,
    val overview: String?,
    /** fr-FR, ou en-US si repli, null si aucun résumé. */
    val overviewLanguage: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val runtimeMin: Int?,
    val genres: List<String>,
    val voteAverage: Double?,
    val voteCount: Int?,
    val cast: List<Pair<String, String?>>,
    val crew: List<String>,
    val seasonCount: Int?,
    val relatedIds: List<Int>,
    val trailerKey: String? = null,
    val logoPath: String? = null,
)

sealed class TmdbError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NoKey : TmdbError("Aucune clé TMDB configurée (Paramètres → TMDB).")
    class InvalidKey : TmdbError("Clé TMDB refusée (HTTP 401). Vérifiez-la dans Paramètres.")
    class NotFound : TmdbError("Élément introuvable sur TMDB.")
    class RateLimited : TmdbError("TMDB limite temporairement les requêtes. Nouvel essai plus tard.")
    class Server(code: Int) : TmdbError("TMDB indisponible (HTTP $code).")
    class Network(cause: Throwable) : TmdbError("TMDB injoignable (${cause.javaClass.simpleName}).", cause)
    class Parse : TmdbError("Réponse TMDB inattendue.")
}
