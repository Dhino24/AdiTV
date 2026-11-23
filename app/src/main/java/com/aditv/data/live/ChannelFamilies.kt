package com.aditv.data.live

import java.text.Normalizer

/**
 * Familles de chaînes du Direct, dans l'ordre voulu par Adama : France et Sénégal d'abord
 * (sport, cinéma, divertissement…), puis le reste du monde par pays. Classement d'après le nom de la chaîne
 * et la catégorie du fournisseur ; une chaîne mal rangée peut être déplacée à la main (Menu ≡).
 */
enum class Family(val label: String, val order: Int) {
    SPORT_FR("Sport France", 10),
    CINEMA_FR("Cinéma France", 20),
    DIVERTISSEMENT_FR("Divertissement France", 30),
    INFO_FR("Info France", 40),
    JEUNESSE_FR("Jeunesse France", 50),
    DOCS_FR("Documentaires France", 60),
    SENEGAL("Sénégal", 70),
    AFRIQUE("Afrique", 80),
    MONDE("Autres pays", 90),
}

/** Rangement d'une chaîne : famille, diffuseur (pour les regroupements), numéro dans la série, pays. */
data class ChannelPlace(val family: Family, val broadcaster: String, val number: Int, val country: String?, val french: Boolean, val ucl: Boolean = false)

object ChannelFamilies {
    private val marks = Regex("\\p{M}+")
    private fun norm(s: String): String {
        // ASCII (la grande majorité des noms) : pas de décomposition Unicode.
        if (s.all { it.code < 128 }) return s.lowercase()
        return marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()
    }
    private val spaces = Regex("""\s{2,}""")
    private val trailingNumber = Regex("""\s*\d+\s*$""")
    private val endNumber = Regex("""(\d+)\s*$""")
    private val catFrance = Regex("""\b(france|french|francais|fr)\b""")
    private val catSenegal = Regex("""\bsenegal""")
    private val catAfrica = Regex("""\b(afrique|africa)\b""")
    private val catCinema = Regex("""\b(cinema|films?)\b""")
    private val catInfo = Regex("""\b(info|news|actualites?)\b""")
    private val catKids = Regex("""\b(jeunesse|enfants?|kids)\b""")
    private val catDocs = Regex("""\bdocumentaires?\b""")

    /** Catégorie fournisseur normalisée + pays déduit : calculé une fois par catégorie (pas par chaîne). */
    private class Cat(val norm: String, val country: String?)
    private val catCache = java.util.concurrent.ConcurrentHashMap<String, Cat>()

    /** Préfixe pays fournisseur : « FR| », « FR: », « [FR] », « |FR| », « FR - ». */
    private val prefix = Regex("""^\s*[\[|(]?\s*([A-Za-z]{2,3})\s*[\]|):\-]\s*""")
    private val quality = Regex("""(?i)\b(uhd|fhd|hd|sd|4k|8k|hevc|h\.?265|h\.?264|50 ?fps|60 ?fps|1080p?|720p?|raw|backup|multi(?:audio)?|vip|\+1)\b|ᴴᴰ|ᶠᴴᴰ|ᵁᴴᴰ|ˢᴰ""")

    private val countries = mapOf(
        "fr" to "France", "fra" to "France", "sn" to "Sénégal", "sen" to "Sénégal", "be" to "Belgique", "bel" to "Belgique",
        "ch" to "Suisse", "sui" to "Suisse", "ca" to "Canada", "qc" to "Canada", "ma" to "Maroc", "dz" to "Algérie", "tn" to "Tunisie",
        "ci" to "Côte d'Ivoire", "cm" to "Cameroun", "ml" to "Mali", "bf" to "Burkina Faso", "gn" to "Guinée", "bj" to "Bénin", "tg" to "Togo",
        "cd" to "RD Congo", "cg" to "Congo", "ga" to "Gabon", "ng" to "Nigeria", "gh" to "Ghana", "af" to "Afrique", "afr" to "Afrique",
        "uk" to "Royaume-Uni", "gb" to "Royaume-Uni", "us" to "États-Unis", "usa" to "États-Unis", "es" to "Espagne", "it" to "Italie",
        "de" to "Allemagne", "pt" to "Portugal", "nl" to "Pays-Bas", "ar" to "Monde arabe", "tr" to "Turquie",
    )
    private val countryNames by lazy { countries.values.distinct().map { norm(it) to it } }
    private val africa = setOf("Côte d'Ivoire", "Cameroun", "Mali", "Burkina Faso", "Guinée", "Bénin", "Togo", "RD Congo", "Congo", "Gabon", "Nigeria", "Ghana", "Afrique")

    // Noms connus (sans accents, minuscules).
    private val sportNames = Regex("""\b(bein|rmc sport|canal\+? ?(sport|foot)|foot\+|eurosport|l ?equipe|infosport|golf\+|automoto|equidia|sport en france|ligue ?1\+?|dazn|multisport|ol ?tv|om ?tv|psg ?tv|tennis|nba|nfl|motorsport|rugby|sports?)\b""")
    private val cinemaNames = Regex("""\b(canal\+? ?(cinema|cinemas|premier|grand ecran)|cine\+?|cine ?(premier|frisson|emotion|famiz|club|classic)|ocs|tcm|paramount|action|polar\+?|warner tv|13e? ?rue|syfy|novelas|cinema|films?)\b""")
    private val infoNames = Regex("""\b(bfm ?tv|bfm|cnews|lci|franceinfo|france ?info|france 24|lcp|public senat|euronews|i24|bfm business|tv5 ?monde info)\b""")
    private val jeunesseNames = Regex("""\b(gulli|canal j|tiji|boomerang|cartoon|disney|nickelodeon|nick|piwi|boing|teletoon|mangas|baby ?tv|kids|enfants?)\b""")
    private val docsNames = Regex("""\b(planete\+?|national geographic|nat ?geo|discovery|histoire|toute l.?histoire|ushuaia|science ?&? ?vie|rmc decouverte|animaux|museum|trek|chasse|seasons|documentaires?)\b""")

    /** Diffuseurs connus (pour regrouper « beIN SPORTS 1, 2, 3… » ou les chaînes d'un même groupe). */
    private val broadcasters = listOf(
        Regex("""\bbein""") to "beIN SPORTS", Regex("""\brmc sport""") to "RMC Sport", Regex("""\bcanal\+? ?sport ?(afrique|africa)|\bcanal\+? .*afrique""") to "Canal+ Afrique",
        Regex("""\bcanal\+?|\bc ?8\b|\bcstar\b|\bcnews\b|\bfoot\+""") to "Canal+", Regex("""\beurosport""") to "Eurosport", Regex("""\bl ?equipe""") to "L'Équipe",
        Regex("""\bocs\b""") to "OCS", Regex("""\bcine\+?\b""") to "Ciné+", Regex("""\b(france [2345]|franceinfo|france info|la1ere|1ere)\b""") to "France Télévisions",
        Regex("""\b(tf1|tmc|tfx|lci|tf1 series)""") to "Groupe TF1", Regex("""\b(m6|w9|6ter|paris premiere|teva|gulli)\b""") to "Groupe M6",
        Regex("""\bbfm""") to "BFM", Regex("""\brmc\b|\brmc (story|decouverte)""") to "RMC", Regex("""\brts\b""") to "RTS", Regex("""\bnew world|nwtv\b""") to "New World TV",
    )

    /** Nom affichable sans préfixe pays ni mention de qualité. */
    fun cleanName(raw: String): String = quality.replace(prefix.replace(raw, ""), "").replace(spaces, " ").trim(' ', '|', '-', ':')

    fun place(rawTitle: String, categoryName: String?, lang: String?): ChannelPlace {
        val code = prefix.find(rawTitle)?.groupValues?.get(1)?.lowercase()
        val clean = cleanName(rawTitle)
        val name = norm(clean)
        val c = catCache.getOrPut(categoryName.orEmpty()) {
            val n = norm(categoryName.orEmpty())
            Cat(n, catSenegal.find(n)?.let { "Sénégal" } ?: catFrance.find(n)?.let { "France" } ?: catAfrica.find(n)?.let { "Afrique" }
                ?: countryNames.firstOrNull { (k, _) -> k in n }?.second)
        }
        val cat = c.norm
        // La catégorie « Sénégal » du fournisseur l'emporte sur le préfixe (« FR: 2STV » rangée dans SÉNÉGAL).
        val country = (if (c.country == "Sénégal") c.country else null) ?: countries[code] ?: c.country ?: if (lang == "fr") "France" else null
        val french = country == "France" || lang == "fr"
        val text = "$name $cat"
        val family = when {
            // Sénégal : uniquement la catégorie « Sénégal » du fournisseur (ou le préfixe SN), pas les noms qui se ressemblent (RTS suisse…).
            country == "Sénégal" -> Family.SENEGAL
            country in africa -> Family.AFRIQUE
            !french -> Family.MONDE
            sportNames.containsMatchIn(name) || cat.contains("sport") -> Family.SPORT_FR
            cinemaNames.containsMatchIn(name) || catCinema.containsMatchIn(cat) -> Family.CINEMA_FR
            infoNames.containsMatchIn(name) || catInfo.containsMatchIn(cat) -> Family.INFO_FR
            jeunesseNames.containsMatchIn(name) || catKids.containsMatchIn(cat) -> Family.JEUNESSE_FR
            docsNames.containsMatchIn(name) || catDocs.containsMatchIn(cat) -> Family.DOCS_FR
            else -> Family.DIVERTISSEMENT_FR
        }
        val broadcaster = broadcasters.firstOrNull { (r, _) -> r.containsMatchIn(text) }?.second
            ?: if (family == Family.MONDE) (country ?: "Autres") else clean.replace(trailingNumber, "").ifBlank { clean }
        val number = endNumber.find(clean)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return ChannelPlace(family, broadcaster, number, country, french, uclBroadcasters.containsMatchIn(name))
    }

    /** Ligue des champions : programme du guide (titre ou catégorie). */
    private val ucl = Regex("""(ligue des champions|champions league|uefa champions|\bldc\b|\bucl\b)""")
    fun isChampionsLeague(title: String, category: String?) = ucl.containsMatchIn(norm("$title ${category.orEmpty()}"))

    /** Diffuseurs de la Ligue des champions 2026-2027 : Canal+ (France), M6 (finale), Canal+ Afrique et New World TV (Afrique francophone). */
    private val uclBroadcasters = Regex("""\b(canal\+? ?(sport|foot|live|sport 360|premier|afrique|africa)?|m6|new world|nwtv)\b""")
    fun isChampionsLeagueBroadcaster(rawTitle: String) = uclBroadcasters.containsMatchIn(norm(cleanName(rawTitle)))
}
