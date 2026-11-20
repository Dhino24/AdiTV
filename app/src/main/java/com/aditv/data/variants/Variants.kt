package com.aditv.data.variants

import com.aditv.core.TitleNormalizer

/**
 * Variantes d'un même contenu chez le fournisseur (« FR| TF1 HD », « FR| TF1 FHD », « FR| TF1 4K »,
 * « FR| Film (2020) VOSTFR »…). La langue et la qualité sont **annoncées par le titre fournisseur**,
 * jamais déduites de TMDB : le flux réel peut différer.
 */
data class VariantInfo(
    /** Clé de regroupement : même contenu, versions différentes. */
    val key: String,
    /** fr | vostfr | multi | en | autre code | null si inconnu */
    val lang: String?,
    /** 4 = 4K/UHD, 3 = FHD/1080p, 2 = HD/720p, 1 = SD, 0 = inconnue */
    val quality: Int,
)

object VariantParser {
    private val prefixCode = Regex("^\\s*[|\\[(]?\\s*([A-Za-z]{2,3})(?:-[A-Za-z]{2,4})?\\s*[|\\])▎:]")
    private val dashPrefix = Regex("^\\s*([A-Z]{2,3})\\s+-\\s+")
    /** Une expression compilée une fois par groupe de mots (des milliers de titres par import). */
    private fun words(vararg w: String) = Regex("(?i)(?<![\\p{L}\\d])(?:" + w.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\d])")
    private val Q4 = words("4K", "UHD", "2160p")
    private val Q3 = words("FHD", "1080p", "FULLHD", "FULL HD")
    private val Q2 = words("HD", "720p")
    private val Q1 = words("SD", "480p", "576p", "LQ")
    private val VOST = words("VOSTFR", "VOST", "SUBFRENCH", "VOST-FR")
    private val MULTI = words("MULTI")
    private val VF = words("VFF", "VFQ", "VFI", "VF", "TRUEFRENCH", "FRENCH")

    fun countryPrefix(raw: String): String? =
        (prefixCode.find(raw) ?: dashPrefix.find(raw))?.groupValues?.get(1)?.lowercase()

    fun quality(raw: String): Int = when {
        Q4.containsMatchIn(raw) -> 4
        Q3.containsMatchIn(raw) -> 3
        Q2.containsMatchIn(raw) -> 2
        Q1.containsMatchIn(raw) -> 1
        else -> 0
    }

    /** Langue annoncée : étiquettes explicites d'abord, puis préfixe pays, puis nom de catégorie. */
    fun language(raw: String, categoryName: String?): String? {
        fun fromText(t: String): String? = when {
            VOST.containsMatchIn(t) -> "vostfr"
            MULTI.containsMatchIn(t) -> "multi"
            VF.containsMatchIn(t) -> "fr"
            else -> null
        }
        fromText(raw)?.let { return it }
        countryPrefix(raw)?.let { return normalizeCountry(it) }
        categoryName?.let { c -> fromText(c)?.let { return it }; countryPrefix(c)?.let { return normalizeCountry(it) } }
        return null
    }

    private fun normalizeCountry(c: String) = when (c) {
        "fr", "fra", "vf" -> "fr"
        "en", "eng", "uk", "us", "gb" -> "en"
        else -> c
    }

    /**
     * Films/séries : même titre nettoyé + même année → même œuvre, quelle que soit la langue.
     * Chaînes : le pays fait partie de l'identité (« BE| RTL » n'est pas « FR| RTL »), pas la qualité.
     */
    fun parse(raw: String, type: String, categoryName: String?, year: Int?): VariantInfo {
        val clean = TitleNormalizer.clean(raw)
        val key = when (type) {
            "LIVE" -> "LIVE|" + (countryPrefix(raw) ?: "") + "|" + clean.searchKey
            else -> type + "|" + clean.searchKey + "|" + (year ?: clean.year ?: "")
        }
        return VariantInfo(key, language(raw, categoryName), quality(raw))
    }

    fun qualityLabel(q: Int?): String? = when (q) { 4 -> "4K"; 3 -> "FHD"; 2 -> "HD"; 1 -> "SD"; else -> null }

    fun languageLabel(l: String?): String? = when (l) {
        null -> null; "fr" -> "VF"; "vostfr" -> "VOSTFR"; "multi" -> "MULTI"; "en" -> "VO anglaise"; else -> l.uppercase()
    }
}

/** Préférences de l'utilisateur (Paramètres → Lecture). */
data class VariantPrefs(
    /** Ordre des langues : par défaut français, puis multi (souvent VF + VO), puis VOSTFR. */
    val languages: List<String> = listOf("fr", "multi", "vostfr"),
    /** Qualité maximale souhaitée (4 = aucune limite). */
    val maxQuality: Int = 4,
) {
    companion object {
        val LANGUAGE_PRESETS = mapOf(
            "vf" to listOf("fr", "multi", "vostfr"),
            "vostfr" to listOf("vostfr", "multi", "fr"),
            "vo" to listOf("en", "multi", "vostfr", "fr"),
        )
    }
}

object VariantRanking {
    /** Plus petit = meilleur : langue préférée d'abord, puis meilleure qualité dans la limite choisie. */
    fun cost(lang: String?, quality: Int?, prefs: VariantPrefs): Int {
        val langRank = prefs.languages.indexOf(lang).let { i -> if (i >= 0) i else if (lang == null) prefs.languages.size else prefs.languages.size + 1 }
        val q = quality ?: 0
        val qualityRank = when {
            q in 1..prefs.maxQuality -> prefs.maxQuality - q // la plus haute autorisée = 0
            q == 0 -> 5 // inconnue : après les qualités connues autorisées
            else -> 6 + (q - prefs.maxQuality) // au-dessus de la limite : en dernier
        }
        return langRank * 100 + qualityRank
    }

    /**
     * Une entrée par contenu : la meilleure variante selon les préférences, à la place de la première
     * rencontrée (l'ordre d'affichage du fournisseur est conservé).
     */
    fun <T> collapse(items: List<T>, key: (T) -> String?, lang: (T) -> String?, quality: (T) -> Int?, prefs: VariantPrefs): List<Pair<T, Int>> {
        val groups = LinkedHashMap<String, MutableList<T>>()
        var anon = 0
        for (it in items) groups.getOrPut(key(it) ?: "#${anon++}") { mutableListOf() }.add(it)
        return groups.values.map { g -> g.minBy { cost(lang(it), quality(it), prefs) } to g.size }
    }
}

/** Regroupement des cartes du catalogue selon les préférences. */
fun List<com.aditv.data.db.CatalogCard>.collapsed(prefs: VariantPrefs): List<Pair<com.aditv.data.db.CatalogCard, Int>> =
    VariantRanking.collapse(this, { it.variantKey }, { it.lang }, { it.quality }, prefs)

/** « VF · 4K » à partir des étiquettes annoncées. */
fun variantBadge(lang: String?, quality: Int?): String? =
    listOfNotNull(VariantParser.languageLabel(lang), VariantParser.qualityLabel(quality)).joinToString(" · ").ifEmpty { null }
