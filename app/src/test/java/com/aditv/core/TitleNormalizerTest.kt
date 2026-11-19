package com.aditv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleNormalizerTest {
    @Test fun stripsCountryPrefixAndTechTags() {
        val c = TitleNormalizer.clean("FR| Les Ombres de Dakar (2026) VOSTFR")
        assertEquals("Les Ombres de Dakar", c.displayTitle)
        assertEquals(2026, c.year)
        assertEquals("les ombres de dakar", c.searchKey)
    }

    @Test fun handlesBracketPrefixAndQuality() {
        val c = TitleNormalizer.clean("[FR] Le Dernier Ferry 4K HDR - 2023")
        assertEquals("Le Dernier Ferry", c.displayTitle)
        assertEquals(2023, c.year)
    }

    @Test fun keepsMeaningfulWordsAndNumbers() {
        assertEquals("2001 l'odyssée de l'espace", TitleNormalizer.clean("FR| 2001 l'odyssée de l'espace").displayTitle)
        assertEquals("Ocean's 11", TitleNormalizer.clean("Ocean's 11").displayTitle)
        assertNull(TitleNormalizer.clean("Ocean's 11").year)
    }

    @Test fun neverReturnsEmptyTitle() {
        assertEquals("4K", TitleNormalizer.clean("4K").displayTitle)
    }

    @Test fun searchKeyRemovesAccents() {
        assertEquals("ete a la plage", TitleNormalizer.searchKey("Été à la plage !"))
    }
}
