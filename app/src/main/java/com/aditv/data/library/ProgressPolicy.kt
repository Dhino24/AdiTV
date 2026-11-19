package com.aditv.data.library

/** Règles de reprise : simples, testées, sans magie. */
object ProgressPolicy {
    /** En dessous, on ne propose pas « Reprendre » (simple aperçu). */
    const val MIN_RESUME_MS = 30_000L

    /** Terminé si ≥ 95 % ou s'il reste moins de 3 min (générique) — pour une durée connue. */
    fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        val remaining = durationMs - positionMs
        return positionMs >= durationMs * 0.95 || (durationMs > 10 * 60_000 && remaining < 3 * 60_000)
    }

    fun resumePosition(positionMs: Long, durationMs: Long, completed: Boolean): Long? =
        if (completed || positionMs < MIN_RESUME_MS || isCompleted(positionMs, durationMs)) null
        // Recule de 5 s pour retrouver le contexte.
        else (positionMs - 5_000).coerceAtLeast(0)

    fun fraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
