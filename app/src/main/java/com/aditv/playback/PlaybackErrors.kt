package com.aditv.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.aditv.net.CleartextNotAllowedException

/** Traduction prudente des erreurs du lecteur : on décrit ce qui est observé, sans deviner une cause invisible. */
object PlaybackErrors {
    fun message(e: PlaybackException): String {
        var c: Throwable? = e
        while (c != null) {
            if (c is CleartextNotAllowedException) return "Flux HTTP en clair vers ${c.host} refusé : cet hôte n'a pas été autorisé à la connexion."
            if (c is HttpDataSource.InvalidResponseCodeException) return when (c.responseCode) {
                401, 403 -> "Le serveur refuse ce flux (HTTP ${c.responseCode}) : accès expiré, contenu non autorisé ou nombre de connexions atteint."
                404 -> "Flux introuvable sur le serveur (HTTP 404)."
                in 500..599 -> "Le serveur a renvoyé une erreur (HTTP ${c.responseCode})."
                else -> "Réponse inattendue du serveur (HTTP ${c.responseCode})."
            }
            c = c.cause
        }
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Connexion au flux impossible."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Le flux ne répond pas dans le délai (réseau ou serveur lent)."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Flux introuvable."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Format de conteneur non pris en charge par Media3. Essayez le moteur VLC (bouton Moteur)."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Format vidéo ou audio non décodable par cet appareil avec Media3."
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "Le direct a pris trop de retard ; reprise au direct nécessaire."
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "Contenu protégé (DRM) : non pris en charge."
            else -> "Lecture interrompue (${e.errorCodeName})."
        }
    }

    /** Format refusé par Media3 (conteneur ou décodage) : seul cas où « Auto » bascule vers VLC. */
    fun formatProblem(e: PlaybackException): Boolean = e.errorCode in setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    )

    /** Erreurs pour lesquelles un nouvel essai a du sens. */
    fun retryable(e: PlaybackException): Boolean = e.errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )
}
