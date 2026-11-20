package com.aditv.data.provider

import java.io.Reader

/**
 * Objet JSON indexé ({"123": {...}, "124": {...}}) lu une entrée à la fois : chaque valeur est découpée
 * en texte (quelques Ko) puis décodée seule. La réponse entière (parfois des dizaines de Mo) n'est jamais en mémoire.
 */
internal object StreamingJson {
    /** Appelle [onEntry] pour chaque couple clé / texte JSON de la valeur. Le lecteur doit être placé sur « { ». */
    inline fun forEachEntry(r: Reader, onEntry: (key: String, valueJson: String) -> Unit) {
        var c = nextSignificant(r)
        require(c == '{'.code) { "objet attendu" }
        while (true) {
            c = nextSignificant(r)
            if (c == '}'.code || c == -1) return
            if (c == ','.code) continue
            require(c == '"'.code) { "clé attendue" }
            val key = readString(r)
            require(nextSignificant(r) == ':'.code) { "« : » attendu" }
            onEntry(key, readValue(r))
        }
    }

    fun nextSignificant(r: Reader): Int {
        while (true) { val c = r.read(); if (c == -1 || !Char(c).isWhitespace()) return c }
    }

    /** Contenu d'une chaîne, guillemet ouvrant déjà lu. */
    fun readString(r: Reader): String {
        val sb = StringBuilder()
        while (true) {
            val c = r.read()
            require(c != -1) { "chaîne interrompue" }
            if (c == '"'.code) return sb.toString()
            if (c == '\\'.code) { sb.append('\\'); sb.append(Char(r.read())) } else sb.append(Char(c))
        }
    }

    /** Texte JSON d'une valeur complète (objet, tableau, chaîne, nombre, littéral). */
    fun readValue(r: Reader): String {
        val sb = StringBuilder()
        var c = nextSignificant(r)
        require(c != -1) { "valeur attendue" }
        if (c == '"'.code) return "\"" + readString(r) + "\""
        if (c != '{'.code && c != '['.code) {
            // Nombre ou littéral : jusqu'au séparateur suivant.
            r.mark(1)
            while (c != -1 && c != ','.code && c != '}'.code && c != ']'.code && !Char(c).isWhitespace()) { sb.append(Char(c)); r.mark(1); c = r.read() }
            if (c != -1) r.reset()
            return sb.toString()
        }
        var depth = 0
        var inString = false
        while (c != -1) {
            sb.append(Char(c))
            if (inString) {
                if (c == '\\'.code) { val n = r.read(); if (n != -1) sb.append(Char(n)) }
                else if (c == '"'.code) inString = false
            } else when (c) {
                '"'.code -> inString = true
                '{'.code, '['.code -> depth++
                '}'.code, ']'.code -> { depth--; if (depth == 0) return sb.toString() }
            }
            c = r.read()
        }
        throw IllegalArgumentException("valeur interrompue")
    }
}
