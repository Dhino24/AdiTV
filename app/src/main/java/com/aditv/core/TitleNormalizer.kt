package com.aditv.core

import java.text.Normalizer

/**
 * Nettoyage des titres fournisseur. Le titre brut est toujours conservé à part.
 * - [displayTitle] retire préfixes pays et étiquettes techniques, garde la casse.
 * - [searchKey] sert à la recherche locale et au futur matching TMDB.
 */
object TitleNormalizer {
    // « FR| », « |FR| », « [FR] », « FR - », « FR: », « AF ▎ » en tête de titre.
    private val prefix = Regex("^\\s*(?:[|\\[(]?\\s*[A-Z]{2,4}(?:-[A-Z]{2,4})?\\s*[|\\])▎:]\\s*|[A-Z]{2,3}\\s+-\\s+)+")
    private val techTags = Regex(
        "(?i)(?<![\\p{L}\\d])(?:vostfr|vost|vf|vff|vfq|multi|truefrench|french|4k|uhd|fhd|hd|sd|hevc|h\\.?26[45]|x26[45]|hdr10\\+?|hdr|dolby\\s*vision|dv|1080p|720p|2160p|480p|web-?dl|webrip|bluray|remux)(?![\\p{L}\\d])"
    )
    private val bracketYear = Regex("[(\\[]\\s*((?:19|20)\\d{2})\\s*[)\\]]")
    private val trailingYear = Regex("[\\s\\-–]+((?:19|20)\\d{2})\\s*$")
    private val emptyBrackets = Regex("[(\\[]\\s*[)\\]]")
    private val separatorsAtEdges = Regex("^[\\s|\\-–:·•_]+|[\\s|\\-–:·•_]+$")
    private val spaces = Regex("\\s{2,}")
    private val diacritics = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    data class Cleaned(val displayTitle: String, val year: Int?, val searchKey: String)

    fun clean(raw: String): Cleaned {
        var t = raw.trim()
        val year = bracketYear.find(t)?.groupValues?.get(1)?.toIntOrNull()
            ?: trailingYear.find(t)?.groupValues?.get(1)?.toIntOrNull()
        t = prefix.replace(t, "")
        t = bracketYear.replace(t, " ")
        t = techTags.replace(t, " ")
        t = emptyBrackets.replace(t, " ")
        if (year != null) t = trailingYear.replace(t, "")
        t = spaces.replace(t, " ")
        t = separatorsAtEdges.replace(t, "")
        // Ne jamais vider un titre : si le nettoyage supprime tout, on garde le brut.
        if (t.isBlank()) t = raw.trim()
        return Cleaned(t, year, searchKey(t))
    }

    fun searchKey(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        return nonAlnum.replace(diacritics.replace(n, ""), " ").trim()
    }
}
