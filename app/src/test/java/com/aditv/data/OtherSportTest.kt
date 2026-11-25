package com.aditv.data

import com.aditv.data.sport.SportMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rappel reçu par Adama : « Real Madrid - Zalgiris Kaunas » (basket, Euroligue) sur Motorsport 02. */
class OtherSportTest {
    @Test fun basketballAndMotorsportAreNotFootball() {
        assertTrue(SportMatcher.isOtherSport("Real Madrid - Zalgiris Kaunas", null, listOf("Motorsport 02")))
        assertTrue(SportMatcher.isOtherSport("Real Madrid - Olympiakos", "Basket-ball", listOf("beIN SPORTS 3")))
        assertTrue(SportMatcher.isOtherSport("Euroligue : Real Madrid - Panathinaikos", null, listOf("Sport TV")))
        assertFalse(SportMatcher.isOtherSport("Liga : Real Madrid - FC Barcelone", "Football", listOf("beIN SPORTS 1")))
        assertFalse(SportMatcher.isOtherSport("Real Madrid - Valence", null, listOf("beIN SPORTS 1", "Motorsport 02")))
    }
}
