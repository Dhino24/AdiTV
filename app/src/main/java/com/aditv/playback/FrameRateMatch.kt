package com.aditv.playback

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Adapter la fréquence de l'écran à la cadence du flux : 50 Hz pour un match diffusé en Europe (25/50 i/s),
 * 24 Hz pour un film. Sans cela, un flux 50 i/s affiché à 60 Hz saccade légèrement dans les mouvements rapides.
 * Calculs purs (testables) ; l'application du mode se fait dans le lecteur.
 */
object FrameRateMatch {
    data class Mode(val id: Int, val width: Int, val height: Int, val refresh: Float)

    private val standard = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 47.952f, 48f, 50f, 59.94f, 60f)

    /** Cadence standard la plus proche (tolérance 1 %), ou null si rien de plausible. */
    fun normalize(fps: Float): Float? = standard.minByOrNull { abs(it - fps) }?.takeIf { abs(it - fps) / it < 0.01f }

    /** Cadence estimée à partir des horodatages des images (µs) : écart médian entre images successives. */
    fun estimate(timestampsUs: List<Long>): Float? {
        if (timestampsUs.size < 12) return null
        val deltas = timestampsUs.sorted().zipWithNext { a, b -> b - a }.filter { it in 5_000..100_000 }
        if (deltas.size < 10) return null
        val median = deltas.sorted()[deltas.size / 2]
        return normalize(1_000_000f / median)
    }

    /**
     * Mode d'affichage à demander : même définition que le mode actuel, fréquence multiple exacte de la cadence
     * (la plus haute, jusqu'à 60 Hz). Null si le mode actuel convient déjà ou si aucun ne correspond.
     */
    fun pick(modes: List<Mode>, current: Mode, fps: Float): Mode? {
        val f = normalize(fps) ?: return null
        fun matches(r: Float): Boolean { val k = (r / f).roundToInt(); return k >= 1 && abs(r - k * f) < 0.01f && r <= 60.5f }
        if (matches(current.refresh) && current.refresh >= f) return null
        return modes.filter { it.width == current.width && it.height == current.height && matches(it.refresh) }
            .maxByOrNull { it.refresh }
    }
}
