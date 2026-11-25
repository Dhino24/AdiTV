package com.aditv.data.sport

import java.text.Normalizer

/** Équipe ou compétition proposée dans Réglages → Sport. */
data class FollowPreset(
    val key: String,
    val name: String,
    val aliases: List<String>,
    val excludes: List<String> = emptyList(),
    val kind: String = "team",
)

/**
 * Reconnaissance des matchs dans le guide, uniquement à partir des données du guide (rien d'inventé).
 * Un programme est retenu pour une équipe si son titre contient un alias de l'équipe (mot entier, accents
 * ignorés), aucun alias exclu, et s'il est en contexte sportif : catégorie sport, chaîne de sport,
 * ou titre de match (« A – B », « A vs B », « A contre B »).
 */
object SportMatcher {
    /** Demandées par Adama : suivies dès l'installation (modifiables dans Réglages → Sport). */
    val DEFAULT_FOLLOWS = listOf("barca", "real", "psg", "mancity", "villa", "senegal")

    val PRESETS = listOf(
        FollowPreset("barca", "FC Barcelone", listOf("fc barcelone", "fc barcelona", "barcelone", "barcelona", "barca"),
            excludes = listOf("espanyol", "barcelona sc", "guayaquil")),
        FollowPreset("real", "Real Madrid", listOf("real madrid")),
        FollowPreset("psg", "Paris Saint-Germain", listOf("paris saint germain", "paris sg", "psg")),
        FollowPreset("mancity", "Manchester City", listOf("manchester city", "man city")),
        FollowPreset("villa", "Aston Villa", listOf("aston villa")),
        FollowPreset("senegal", "Sénégal", listOf("senegal", "lions de la teranga")),
        FollowPreset("france", "Équipe de France", listOf("equipe de france", "france"), excludes = listOf("tour de france", "coupe de france", "france 2", "france 3", "france 4", "france 5", "france tv", "ligue de france")),
        FollowPreset("om", "Olympique de Marseille", listOf("olympique de marseille", "marseille")),
        FollowPreset("ol", "Olympique lyonnais", listOf("olympique lyonnais", "lyon")),
        FollowPreset("liverpool", "Liverpool", listOf("liverpool")),
        FollowPreset("arsenal", "Arsenal", listOf("arsenal")),
        FollowPreset("bayern", "Bayern Munich", listOf("bayern munich", "bayern munchen", "bayern")),
        FollowPreset("ucl", "Ligue des champions", listOf("ligue des champions", "champions league"), kind = "competition"),
        FollowPreset("can", "Coupe d'Afrique des nations", listOf("coupe d afrique des nations", "africa cup of nations"), kind = "competition"),
        FollowPreset("ligue1", "Ligue 1", listOf("ligue 1"), kind = "competition"),
        FollowPreset("liga", "Liga", listOf("liga"), excludes = listOf("liga portugal"), kind = "competition"),
        FollowPreset("pl", "Premier League", listOf("premier league"), kind = "competition"),
    )

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    private val SPORT_WORDS = Regex("\\b(sport|sports|football|foot|soccer|ligue|liga|league|champions|coupe|cup|calcio|bundesliga|serie a|match|rugby|basket|basketball|tennis|f1|formule 1|handball|athletisme|cyclisme|boxe|mma|ufc|nba)\\b")
    private val SPORT_CHANNEL = Regex("\\b(bein|canal sport|canal foot|canal\\+ sport|canal\\+ foot|rmc sport|eurosport|l equipe|lequipe|equipe|infosport|dazn|sport|sports|foot|golf|automoto|equidia|ligue 1)\\b")
    /** Titre de rencontre : deux camps séparés par un tiret, « vs », « v » ou « contre ». */
    private val MATCH_TITLE = Regex("(?i)\\S\\s+[-–—]\\s+\\S|\\bvs\\.?\\s|\\sv\\s|\\bcontre\\b")
    private val NOT_LIVE = Regex("\\b(rediff|rediffusion|replay|resume|resumes|highlights|best of|magazine|retro|documentaire)\\b")

    fun isSportChannel(name: String, category: String?): Boolean =
        SPORT_CHANNEL.containsMatchIn(normalize(name)) || (category != null && SPORT_WORDS.containsMatchIn(normalize(category)))

    fun isMatchTitle(title: String) = MATCH_TITLE.containsMatchIn(title)

    fun isSportContext(title: String, category: String?, onSportChannel: Boolean): Boolean =
        onSportChannel || isMatchTitle(title) || (category != null && SPORT_WORDS.containsMatchIn(normalize(category)))

    /**
     * Autre sport que le foot (les équipes suivies sont des clubs de foot) : catégorie ou titre qui le dit, chaîne de sport
     * mécanique ou de basket, ou adversaire qui n'existe qu'en basket (Real Madrid – Zalgiris Kaunas = Euroligue).
     */
    private val OTHER_SPORT = Regex("\\b(basket|basketball|nba|euroleague|euroligue|eurocup|handball|volley|volleyball|rugby|top 14|hockey|tennis|padel|f1|formule 1|motogp|moto gp|cyclisme|boxe|mma|ufc|nfl)\\b")
    private val OTHER_SPORT_CHANNEL = Regex("\\b(motorsport|automoto|basket|nba|rugby|golf|tennis|equidia)\\b")
    private val BASKET_ONLY_CLUBS = Regex("\\b(zalgiris|baskonia|efes|asvel|virtus|olimpia milano|armani|alba berlin|valencia basket|unicaja|paris basketball|monaco basket|maccabi playtika|partizan mozzart|crvena zvezda meridianbet|hapoel ibi|dubai basketball|real madrid baloncesto)\\b")

    fun isOtherSport(title: String, category: String?, channelNames: List<String>): Boolean {
        val t = normalize(title)
        return OTHER_SPORT.containsMatchIn(t) || BASKET_ONLY_CLUBS.containsMatchIn(t) ||
            (category != null && OTHER_SPORT.containsMatchIn(normalize(category))) ||
            channelNames.isNotEmpty() && channelNames.all { OTHER_SPORT_CHANNEL.containsMatchIn(normalize(it)) }
    }

    /** Rediffusion, résumé, magazine : affichés, mais jamais de rappel automatique. */
    fun isLikelyNotLive(title: String, description: String?) =
        NOT_LIVE.containsMatchIn(normalize(title)) || (description != null && Regex("\\b(rediffusion|en differe)\\b").containsMatchIn(normalize(description)))

    /** L'équipe (ou compétition) apparaît-elle dans le titre ? Mot entier, accents ignorés, exclusions appliquées. */
    fun mentions(title: String, aliases: List<String>, excludes: List<String>): Boolean {
        val t = " " + normalize(title) + " "
        if (excludes.any { e -> t.contains(" " + normalize(e) + " ") }) return false
        return aliases.any { a -> t.contains(" " + normalize(a) + " ") }
    }

    /** Clé d'un match, commune aux chaînes qui le diffusent : titre normalisé + heure de début arrondie à 5 min. */
    fun matchKey(title: String, startUtc: Long): String = normalize(title) + "@" + (startUtc / 300_000L)

    /**
     * Motifs SQL (LIKE) larges : « e » et « c » deviennent « _ » (un caractère quelconque) pour couvrir é, è, ç ;
     * la vérification exacte se fait ensuite avec [mentions].
     */
    fun likePatterns(aliases: List<String>): List<String> =
        aliases.map { a -> normalize(a).replace('e', '_').replace('c', '_').replace(' ', '%') }
}
