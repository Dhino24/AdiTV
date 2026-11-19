package com.aditv.data.epg

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class XChannel(val id: String, val displayName: String, val iconUrl: String?)

data class XProgramme(
    val channelId: String,
    val startUtc: Long,
    val endUtc: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val episode: String?,
)

data class XmltvStats(
    val channels: Int,
    val programmes: Int,
    val outOfWindow: Int,
    val unwantedChannel: Int,
    /** Horaires sans fuseau : interprétés dans le fuseau par défaut (serveur) et comptés. */
    val ambiguousTimes: Int,
    /** Fin absente déduite du début du programme suivant de la même chaîne. */
    val inferredEnds: Int,
    /** Programmes ignorés (dates illisibles, fin absente non déductible, fin avant début). */
    val invalid: Int,
)

class XmltvTooLargeException(limit: Long) : IOException("Guide XMLTV trop volumineux (> ${limit / 1_000_000} Mo décompressés).")

/**
 * Lecture XMLTV en flux (aucun chargement complet en mémoire), compressée ou non.
 * DTD et entités externes non traitées. Les horaires sont convertis en UTC ; une fin absente n'est
 * déduite que du début du programme suivant de la même chaîne, sinon le programme est ignoré.
 */
class XmltvParser(
    private val defaultZone: ZoneId,
    private val windowFrom: Long,
    private val windowTo: Long,
    /** Chaînes à conserver (null = toutes). */
    private val wanted: Set<String>?,
    private val maxBytes: Long = 400L * 1024 * 1024,
) {
    private var ambiguous = 0

    fun parse(raw: InputStream, onChannel: (XChannel) -> Unit, onProgramme: (XProgramme) -> Unit): XmltvStats {
        val input = decompressIfNeeded(BufferedInputStream(raw, 64 * 1024))
        val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        runCatching { p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        p.setInput(Limited(input, maxBytes), null)

        var channels = 0; var programmes = 0; var outOfWindow = 0; var unwanted = 0; var inferred = 0; var invalid = 0
        val pending = HashMap<String, PendingProgramme>()

        fun emit(prog: XProgramme) {
            if (prog.endUtc <= prog.startUtc) { invalid++; return }
            if (prog.endUtc <= windowFrom || prog.startUtc >= windowTo) { outOfWindow++; return }
            programmes++; onProgramme(prog)
        }

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) when (p.name) {
                "channel" -> {
                    val id = p.getAttributeValue(null, "id")
                    val c = readChannel(p, id)
                    if (c != null) { channels++; onChannel(c) }
                }
                "programme" -> {
                    val ch = p.getAttributeValue(null, "channel")
                    val start = p.getAttributeValue(null, "start")?.let(::parseTime)
                    val stop = p.getAttributeValue(null, "stop")?.let(::parseTime)
                    val body = readProgrammeBody(p)
                    when {
                        ch == null || start == null -> invalid++
                        wanted != null && ch !in wanted -> unwanted++
                        else -> {
                            // Une fin manquante précédente se déduit de ce début.
                            pending.remove(ch)?.let { prev -> inferred++; emit(prev.toProgramme(ch, start)) }
                            if (stop != null) emit(XProgramme(ch, start, stop, body.title, body.desc, body.category, body.episode))
                            else pending[ch] = PendingProgramme(start, body)
                        }
                    }
                }
            }
            event = p.next()
        }
        invalid += pending.size // fins non déductibles : ignorées plutôt qu'inventées
        return XmltvStats(channels, programmes, outOfWindow, unwanted, ambiguous, inferred, invalid)
    }

    private data class Body(val title: String, val desc: String?, val category: String?, val episode: String?)
    private data class PendingProgramme(val start: Long, val body: Body) {
        fun toProgramme(ch: String, end: Long) = XProgramme(ch, start, end, body.title, body.desc, body.category, body.episode)
    }

    private fun readChannel(p: XmlPullParser, id: String?): XChannel? {
        var name: String? = null; var nameLangFr = false; var icon: String? = null
        val depth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "display-name" -> {
                    val fr = p.getAttributeValue(null, "lang")?.startsWith("fr") == true
                    val t = p.nextText().trim()
                    if (t.isNotEmpty() && (name == null || (fr && !nameLangFr))) { name = t; nameLangFr = fr }
                }
                "icon" -> icon = p.getAttributeValue(null, "src")?.takeIf { it.startsWith("http", true) }
            }
        }
        return id?.let { XChannel(it, name ?: it, icon) }
    }

    /** Titre, résumé et catégorie : version française préférée si plusieurs langues. */
    private fun readProgrammeBody(p: XmlPullParser): Body {
        var title: String? = null; var titleFr = false
        var desc: String? = null; var descFr = false
        var cat: String? = null; var ep: String? = null
        val depth = p.depth
        while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) break
            if (p.eventType != XmlPullParser.START_TAG) continue
            val fr = p.getAttributeValue(null, "lang")?.startsWith("fr") == true
            when (p.name) {
                "title" -> { val t = p.nextText().trim(); if (t.isNotEmpty() && (title == null || (fr && !titleFr))) { title = t; titleFr = fr } }
                "desc" -> { val t = p.nextText().trim(); if (t.isNotEmpty() && (desc == null || (fr && !descFr))) { desc = t; descFr = fr } }
                "category" -> { val t = p.nextText().trim(); if (cat == null && t.isNotEmpty()) cat = t }
                "episode-num" -> { val sys = p.getAttributeValue(null, "system"); val t = p.nextText().trim(); if (sys == "onscreen" || ep == null) ep = t.ifEmpty { null } }
            }
        }
        return Body(title ?: "Sans titre", desc, cat, ep)
    }

    /** « 20261119200000 +0100 », « 202611192000 », « 20261119200000 Z ». Sans fuseau : fuseau par défaut (compté). */
    fun parseTime(v: String): Long? {
        val m = TIME.find(v.trim()) ?: return null
        val (y, mo, d, h, mi) = m.destructured.toList().take(5).map { it.toInt() }
        val s = m.groupValues[6].ifEmpty { "0" }.toInt()
        val local = runCatching { LocalDateTime.of(y, mo, d, h, mi, s) }.getOrNull() ?: return null
        val off = m.groupValues[7]
        return when {
            off == "Z" || off == "+0000" || off == "-0000" -> local.toInstant(ZoneOffset.UTC).toEpochMilli()
            off.isNotEmpty() -> local.toInstant(ZoneOffset.of(off.substring(0, 3) + ":" + off.substring(3))).toEpochMilli()
            else -> { ambiguous++; local.atZone(defaultZone).toInstant().toEpochMilli() } // changements d'heure gérés par la zone
        }
    }

    private class Limited(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0 && ++count > limit) throw XmltvTooLargeException(limit) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { count += it; if (count > limit) throw XmltvTooLargeException(limit) } }
    }

    companion object {
        private val TIME = Regex("^(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})?\\s*([+-]\\d{4}|Z)?")

        fun decompressIfNeeded(input: BufferedInputStream): InputStream {
            input.mark(2)
            val b1 = input.read(); val b2 = input.read()
            input.reset()
            return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(input, 64 * 1024) else input
        }
    }
}
