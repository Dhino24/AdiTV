package com.aditv.variants

import com.aditv.data.db.CatalogCard
import com.aditv.data.variants.VariantParser
import com.aditv.data.variants.VariantPrefs
import com.aditv.data.variants.collapsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VariantsTest {
    @Test fun detectsQualityAndLanguage() {
        assertEquals(4, VariantParser.quality("FR| TF1 4K"))
        assertEquals(4, VariantParser.quality("FR| TF1 UHD"))
        assertEquals(3, VariantParser.quality("FR| TF1 FHD"))
        assertEquals(2, VariantParser.quality("FR| TF1 HD"))
        assertEquals(1, VariantParser.quality("FR| TF1 SD"))
        assertEquals(0, VariantParser.quality("FR| TF1"))
        assertEquals("vostfr", VariantParser.language("FR| Film (2020) VOSTFR", null))
        assertEquals("fr", VariantParser.language("Film (2020) TRUEFRENCH", null))
        assertEquals("multi", VariantParser.language("Film MULTi 1080p", null))
        assertEquals("fr", VariantParser.language("FR| Film (2020)", null))
        assertEquals("en", VariantParser.language("EN| Film (2020)", null))
        assertEquals("vostfr", VariantParser.language("Film (2020)", "VOSTFR | Films"))
        assertNull(VariantParser.language("Film (2020)", "Action"))
    }

    @Test fun groupsSameContentButKeepsCountriesApartForChannels() {
        val a = VariantParser.parse("FR| TF1 HD", "LIVE", null, null).key
        assertEquals(a, VariantParser.parse("FR| TF1 4K", "LIVE", null, null).key)
        assertEquals(a, VariantParser.parse("FR| TF1", "LIVE", null, null).key)
        assertNotEquals("BE| TF1 n'est pas FR| TF1", a, VariantParser.parse("BE| TF1 HD", "LIVE", null, null).key)
        assertNotEquals(a, VariantParser.parse("FR| TF1 Séries Films HD", "LIVE", null, null).key)
        val m = VariantParser.parse("FR| Dune (2021) VOSTFR", "MOVIE", null, 2021).key
        assertEquals(m, VariantParser.parse("EN| Dune (2021) 4K", "MOVIE", null, 2021).key)
        assertNotEquals("remake = autre œuvre", m, VariantParser.parse("FR| Dune (1984)", "MOVIE", null, 1984).key)
    }

    private fun card(id: String, raw: String, type: String = "LIVE") = VariantParser.parse(raw, type, null, null).let {
        CatalogCard(id, type, raw, null, null, null, variantKey = it.key, lang = it.lang, quality = it.quality)
    }

    @Test fun picksFrenchThenHighestQualityWithinLimit() {
        val list = listOf(card("sd", "FR| TF1 SD"), card("4k", "FR| TF1 4K"), card("hd", "FR| TF1 HD"), card("m6", "FR| M6 HD"))
        val best = list.collapsed(VariantPrefs())
        assertEquals(listOf("4k", "m6"), best.map { it.first.id })
        assertEquals(3, best.first().second)
        assertEquals("hd", list.collapsed(VariantPrefs(maxQuality = 2)).first().first.id)

        val films = listOf(card("vo", "FR| Film X (2020) VOSTFR 4K", "MOVIE"), card("vf", "FR| Film X (2020) VF HD", "MOVIE"))
        assertEquals("VF d'abord même en qualité inférieure", "vf", films.collapsed(VariantPrefs()).single().first.id)
        assertEquals("vo", films.collapsed(VariantPrefs(languages = VariantPrefs.LANGUAGE_PRESETS.getValue("vostfr"))).single().first.id)
    }
}
