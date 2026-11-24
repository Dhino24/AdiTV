package com.aditv.data

import com.aditv.data.live.ChannelFamilies
import com.aditv.data.live.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelFamiliesTest {
    private fun fam(t: String, cat: String? = null) = ChannelFamilies.place(t, cat, null).family

    @Test fun frenchAndSenegalFirst() {
        assertEquals(Family.SPORT_FR, fam("FR| beIN SPORTS 1 FHD"))
        assertEquals(Family.SPORT_FR, fam("FR| RMC Sport 2 HD"))
        assertEquals(Family.SPORT_FR, fam("FR: Canal+ Foot"))
        assertEquals(Family.CINEMA_FR, fam("FR| Ciné+ Frisson"))
        assertEquals(Family.CINEMA_FR, fam("FR| OCS Max"))
        assertEquals(Family.DIVERTISSEMENT_FR, fam("FR| TF1 FHD"))
        assertEquals(Family.DIVERTISSEMENT_FR, fam("FR| M6"))
        assertEquals(Family.INFO_FR, fam("FR| BFM TV"))
        assertEquals(Family.JEUNESSE_FR, fam("FR| Gulli"))
        assertEquals(Family.DOCS_FR, fam("FR| National Geographic"))
        assertEquals(Family.SENEGAL, fam("SN| RTS 1"))
        assertEquals(Family.SENEGAL, fam("2STV", "AFRIQUE | Sénégal"))
        // Seule la catégorie Sénégal du fournisseur compte : une « RTS » suisse ou une chaîne d'Afrique n'y vont pas.
        assertEquals(Family.AFRIQUE, fam("TFM HD", "Afrique"))
        assertEquals(Family.SENEGAL, fam("FR: 2STV", "SENEGAL"))
        assertEquals(Family.MONDE, fam("CH| RTS Un"))
        assertEquals(Family.AFRIQUE, fam("CI| RTI 1"))
        assertEquals(Family.MONDE, fam("UK| Sky Sports Main Event"))
        assertEquals(Family.SPORT_FR, fam("Eurosport 1", "France | Sport"))
    }

    @Test fun broadcastersAndNumbers() {
        val p = ChannelFamilies.place("FR| beIN SPORTS 3 HD", null, null)
        assertEquals("beIN SPORTS", p.broadcaster); assertEquals(3, p.number)
        assertEquals("Canal+", ChannelFamilies.place("FR| Canal+ Sport 360", null, null).broadcaster)
        assertEquals("beIN SPORTS 1", ChannelFamilies.cleanName("FR| beIN SPORTS 1 FHD"))
    }

    @Test fun championsLeague() {
        assertTrue(ChannelFamilies.isChampionsLeague("Ligue des champions : PSG - Real Madrid", null))
        assertTrue(ChannelFamilies.isChampionsLeague("Football", "UEFA Champions League"))
        assertFalse(ChannelFamilies.isChampionsLeague("Ligue 1 : PSG - Lens", "Football"))
        assertTrue(ChannelFamilies.isChampionsLeagueBroadcaster("FR| Canal+ Foot FHD"))
        assertTrue(ChannelFamilies.isChampionsLeagueBroadcaster("AF| Canal+ Sport 1 Afrique"))
        assertFalse(ChannelFamilies.isChampionsLeagueBroadcaster("FR| beIN SPORTS 1"))
    }

    @Test fun canalSportIncludesLiveChannels() {
        listOf("FR| CANAL+ SPORT HD", "CANAL+ FOOT", "FR: Canal+ Live 3", "C+ RÉU| CANAL+ SPORT", "FR| CANAL+ SPORT 360", "Canal+ Premier League")
            .forEach { assertTrue(it, ChannelFamilies.isCanalSport(it)) }
        listOf("FR| CANAL+ HD", "FR| Canal+ Cinéma(s)", "FR| CANAL+ SÉRIES", "beIN SPORTS 1").forEach { assertFalse(it, ChannelFamilies.isCanalSport(it)) }
    }
}
