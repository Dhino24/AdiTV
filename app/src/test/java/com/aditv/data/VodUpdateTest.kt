package com.aditv.data

import com.aditv.di.vodDueDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Films et séries : mise à jour proposée toutes les 3 semaines, jamais imposée. */
class VodUpdateTest {
    private val day = 86_400_000L
    private val now = 1_795_000_000_000L

    @Test fun proposedAfterThreeWeeksOnly() {
        assertNull("jamais importé : import automatique, pas de proposition", vodDueDays(null, now, 0, false))
        assertNull(vodDueDays(now - 20 * day, now, 0, false))
        assertEquals(21, vodDueDays(now - 21 * day, now, 0, false))
        assertNull("« Plus tard » respecté", vodDueDays(now - 30 * day, now, now + 3 * day, false))
        assertEquals(30, vodDueDays(now - 30 * day, now, now - 1, false))
        assertNull("déjà en cours", vodDueDays(now - 30 * day, now, 0, true))
    }
}
