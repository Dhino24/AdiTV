package com.aditv.data.tmdb

import com.aditv.core.TitleNormalizer
import kotlin.math.abs

/**
 * Score multi-signal titre / titre original / année. Ne valide jamais automatiquement
 * un premier résultat douteux : en cas d'écart faible entre candidats, la correspondance reste « ambiguë ».
 */
object TmdbMatcher {
    const val CONFIRM = 0.85
    const val MARGIN = 0.12
    const val AMBIGUOUS = 0.55

    enum class Status { CONFIRMED, AMBIGUOUS, UNMATCHED }

    data class Decision(val status: Status, val best: TmdbCandidate?, val confidence: Double, val ranked: List<Pair<TmdbCandidate, Double>>)

    fun similarity(a: String, b: String?): Double {
        if (b.isNullOrBlank()) return 0.0
        val ka = TitleNormalizer.searchKey(a)
        val kb = TitleNormalizer.searchKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return 0.0
        if (ka == kb) return 1.0
        val ta = ka.split(' ').filter { it.isNotEmpty() }
        val tb = kb.split(' ').filter { it.isNotEmpty() }
        val inter = ta.toSet().intersect(tb.toSet()).size
        val dice = 2.0 * inter / (ta.toSet().size + tb.toSet().size)
        // « Titre » vs « Titre : sous-titre » : proche mais pas identique.
        val prefix = if (ta.size >= 2 && tb.size >= 2 && (kb.startsWith("$ka ") || ka.startsWith("$kb "))) 0.8 else 0.0
        return maxOf(dice, prefix)
    }

    fun score(title: String, year: Int?, c: TmdbCandidate): Double {
        var s = maxOf(similarity(title, c.title), similarity(title, c.originalTitle)) * 0.75
        s += when {
            year == null || c.year == null -> 0.08
            abs(year - c.year) == 0 -> 0.25
            abs(year - c.year) == 1 -> 0.15 // sortie décalée selon les pays
            else -> -0.25
        }
        return s.coerceIn(0.0, 1.0)
    }

    fun decide(title: String, year: Int?, candidates: List<TmdbCandidate>): Decision {
        val ranked = candidates.map { it to score(title, year, it) }
            // À score égal, la popularité départage l'ordre d'affichage, pas la décision.
            .sortedWith(compareByDescending<Pair<TmdbCandidate, Double>> { it.second }.thenByDescending { it.first.popularity })
        val best = ranked.firstOrNull() ?: return Decision(Status.UNMATCHED, null, 0.0, emptyList())
        val second = ranked.getOrNull(1)?.second ?: 0.0
        val status = when {
            best.second >= CONFIRM && best.second - second >= MARGIN -> Status.CONFIRMED
            best.second >= AMBIGUOUS -> Status.AMBIGUOUS
            else -> Status.UNMATCHED
        }
        return Decision(status, best.first.takeIf { status != Status.UNMATCHED }, best.second, ranked.take(8))
    }
}
