package com.aditv.playback.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Moteur réellement utilisé pour une session. */
enum class EngineKind(val label: String) { MEDIA3("Media3"), VLC("VLC") }

/** Choix de l'utilisateur, mémorisé par contenu (Auto = Media3, puis VLC une seule fois si le format est refusé). */
enum class EngineChoice(val label: String, val detail: String) {
    AUTO("Auto", "Media3, puis VLC une fois si le format n'est pas pris en charge"),
    MEDIA3("Media3", "Lecteur Android, le plus léger pour la Stick"),
    VLC("VLC", "Lit davantage de formats ; plus gourmand"),
}

/** Nature d'une mesure : jamais une valeur d'un moteur recopiée dans l'autre. */
enum class Nature(val label: String) { MEASURED("Mesuré"), ESTIMATED("Estimé"), DECLARED("Déclaré"), NA("N/D") }

data class StatRow(val label: String, val value: String, val nature: Nature)

/** Piste audio ou de sous-titres exposée par le moteur. */
data class EngineTrack(val group: Int, val id: Int, val label: String, val selected: Boolean)

/** Événements remontés par le moteur au lecteur. */
interface EngineEvents {
    fun onError(message: String, retryable: Boolean, formatProblem: Boolean)
    fun onEnded()
    fun onPlayingChanged(playing: Boolean)
    /** Cadence de la vidéo (images/s), dès qu'elle est connue. */
    fun onFrameRate(fps: Float) {}
}

/** Paramètres de mémoire tampon (Turbo : réserve étendue, plafond mémoire). */
data class BufferPolicy(val turbo: Boolean) {
    /** Media3 : réserve minimale / maximale et reprise après coupure (ms), plafond mémoire (octets). */
    val minBufferMs get() = if (turbo) 60_000 else 30_000
    val maxBufferMs get() = if (turbo) com.aditv.core.DeviceProfile.turboMaxBufferMs else 50_000
    val rebufferMs get() = if (turbo) 5_000 else 2_500
    /** Plafond : Fire TV Stick 1re génération (1,5 Go de RAM partagée), jamais plus de 96 Mo de tampon. */
    val targetBytes get() = if (turbo) com.aditv.core.DeviceProfile.turboTargetBytes else -1
    /** VLC : mise en cache réseau (ms). */
    val vlcNetworkCachingMs get() = if (turbo) com.aditv.core.DeviceProfile.vlcTurboCachingMs else 2_000
}

/**
 * Contrat commun aux moteurs (Media3, LibVLC) : le lecteur ne connaît que cette interface.
 * Une seule session existe à la fois (voir PlaybackController).
 */
interface PlaybackEngine {
    val kind: EngineKind
    val isPlaying: Boolean
    val isBuffering: Boolean
    val positionMs: Long
    /** 0 si inconnue (direct). */
    val durationMs: Long
    val bufferedMs: Long
    val seekable: Boolean
    /** Interruptions mesurées (passage en chargement après un premier démarrage). */
    val stalls: Int
    /** Délai mesuré entre l'ouverture et la première image prête. */
    val startupMs: Long?
    /** Images perdues mesurées par le décodeur (null si non exposé). */
    val droppedFrames: Int?

    fun play()
    fun pause()
    fun seekTo(ms: Long)
    /** Ouverture en pause (retour au premier plan). */
    fun holdPaused()

    fun audioTracks(): List<EngineTrack>
    fun textTracks(): List<EngineTrack>
    val textDisabled: Boolean
    fun selectAudio(t: EngineTrack)
    /** null : sous-titres désactivés. */
    fun selectText(t: EngineTrack?)

    fun stats(): List<StatRow>
    fun release()

    @Composable fun VideoSurface(modifier: Modifier)
}

/** Politique « Auto » : une seule bascule vers VLC, seulement pour un format refusé par Media3 (jamais pour une panne réseau). */
object EnginePolicy {
    fun initial(choice: EngineChoice): EngineKind = if (choice == EngineChoice.VLC) EngineKind.VLC else EngineKind.MEDIA3
    fun shouldFallback(choice: EngineChoice, current: EngineKind, formatProblem: Boolean, alreadyFellBack: Boolean): Boolean =
        choice == EngineChoice.AUTO && current == EngineKind.MEDIA3 && formatProblem && !alreadyFellBack
}
