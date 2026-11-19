package com.aditv.epg

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.data.epg.XProgramme
import com.aditv.data.epg.XmltvParser
import com.aditv.data.epg.XmltvTooLargeException
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class XmltvParserTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private val all = ms("2026-01-01T00:00:00Z") to ms("2027-01-01T00:00:00Z")

    private fun parse(xml: String, wanted: Set<String>? = null, window: Pair<Long, Long> = all, max: Long = 10_000_000): Pair<List<XProgramme>, com.aditv.data.epg.XmltvStats> {
        val out = mutableListOf<XProgramme>()
        val stats = XmltvParser(paris, window.first, window.second, wanted, max).parse(xml.byteInputStream(), {}, { out += it })
        return out to stats
    }

    @Test fun dossierSampleIsConvertedToUtc() {
        // 07_Donnees_demo/epg_fictif.xml
        val (p, s) = parse("""<?xml version="1.0" encoding="UTF-8"?><tv><channel id="aditv.demo"><display-name>ADITV Démo</display-name></channel>
            <programme channel="aditv.demo" start="20261119200000 +0100" stop="20261119210000 +0100"><title lang="fr">Programme fictif</title><desc lang="fr">Exemple</desc><category lang="fr">Démonstration</category></programme></tv>""")
        assertEquals(1, s.channels)
        assertEquals(ms("2026-11-19T19:00:00Z"), p.single().startUtc)
        assertEquals(ms("2026-11-19T20:00:00Z"), p.single().endUtc)
        assertEquals("Démonstration", p.single().category)
    }

    @Test fun timesWithoutOffsetUseZoneAndDaylightSaving() {
        val (p, s) = parse("""<tv>
            <programme channel="c" start="20260701200000" stop="20260701210000"><title>Été</title></programme>
            <programme channel="c" start="20261201200000" stop="20261201210000"><title>Hiver</title></programme>
            <programme channel="d" start="20260329013000 +0100" stop="20260329033000 +0200"><title>Passage à l'heure d'été</title></programme></tv>""")
        assertEquals(ms("2026-07-01T18:00:00Z"), p[0].startUtc)
        assertEquals(ms("2026-12-01T19:00:00Z"), p[1].startUtc)
        assertEquals("le programme dure 1 h réelle", 3_600_000L, p[2].endUtc - p[2].startUtc)
        assertEquals(4, s.ambiguousTimes)
    }

    @Test fun missingStopIsInferredFromNextOnlyAndFrenchIsPreferred() {
        val (p, s) = parse("""<tv>
            <programme channel="c" start="20261119180000 +0000"><title lang="en">News</title><title lang="fr">Journal</title></programme>
            <programme channel="c" start="20261119190000 +0000" stop="20261119200000 +0000"><title>Film</title></programme>
            <programme channel="c" start="20261119230000 +0000" stop="20261119220000 +0000"><title>Fin avant début</title></programme>
            <programme channel="c" start="20261119235000 +0000"><title>Sans fin connue</title></programme></tv>""")
        assertEquals(listOf("Journal", "Film"), p.map { it.title })
        assertEquals(ms("2026-11-19T19:00:00Z"), p[0].endUtc)
        assertEquals(1, s.inferredEnds)
        assertEquals("fin inventée refusée + fin avant début", 2, s.invalid)
    }

    @Test fun windowAndWantedChannelsFilter() {
        val xml = """<tv><programme channel="a" start="20261119180000 +0000" stop="20261119190000 +0000"><title>A</title></programme>
            <programme channel="b" start="20261119180000 +0000" stop="20261119190000 +0000"><title>B</title></programme>
            <programme channel="a" start="20261230180000 +0000" stop="20261230190000 +0000"><title>Trop tard</title></programme></tv>"""
        val (p, s) = parse(xml, wanted = setOf("a"), window = ms("2026-11-19T00:00:00Z") to ms("2026-11-27T00:00:00Z"))
        assertEquals(listOf("A"), p.map { it.title })
        assertEquals(1, s.unwantedChannel); assertEquals(1, s.outOfWindow)
    }

    @Test fun externalEntitiesAreNeverResolved() {
        val xml = """<?xml version="1.0"?><!DOCTYPE tv [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <tv><programme channel="c" start="20261119180000 +0000" stop="20261119190000 +0000"><title>&xxe;</title></programme></tv>"""
        try {
            val (p, _) = parse(xml)
            assertFalse(p.any { it.title.contains("root:") })
        } catch (e: org.xmlpull.v1.XmlPullParserException) { /* entité refusée : attendu */ }
    }

    @Test fun gzipAndSizeLimit() {
        val xml = """<tv><programme channel="c" start="20261119180000 +0000" stop="20261119190000 +0000"><title>Gz</title></programme></tv>"""
        val bytes = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(xml.toByteArray()) } }.toByteArray()
        val out = mutableListOf<XProgramme>()
        XmltvParser(paris, all.first, all.second, null).parse(bytes.inputStream(), {}, { out += it })
        assertEquals("Gz", out.single().title)
        try { parse(xml.repeat(1), max = 40); fail() } catch (e: XmltvTooLargeException) { assertTrue(e.message!!.contains("trop volumineux")) }
    }
}
