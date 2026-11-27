package com.aditv.data

import com.aditv.data.account.SubscriptionHealth
import com.aditv.data.account.SubscriptionHealth.Level
import com.aditv.data.db.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionHealthTest {
    private val now = 1_800_000_000_000L
    private fun acc(status: String? = "Active", expSec: Long? = null, max: Int? = 2) = AccountEntity(
        id = "a", kind = "XTREAM", endpoint = "https://x", secretRef = null, cleartextConsent = false, createdAt = 0,
        status = status, expiresAtEpochSec = expSec, maxConnections = max, activeConnections = 1, isTrial = false, serverTimezone = null, statusCheckedAt = now,
    )

    @Test fun farExpiryIsOk() {
        val h = SubscriptionHealth.assess(acc(expSec = now / 1000 + 40 * 86_400), now)
        assertEquals(Level.OK, h.level); assertEquals(40L, h.daysLeft)
    }
    @Test fun withinAWeekWarns() {
        val h = SubscriptionHealth.assess(acc(expSec = now / 1000 + 5 * 86_400 + 60), now)
        assertEquals(Level.SOON, h.level); assertTrue(h.title.contains("5 jours")); assertTrue(h.needsAttention)
    }
    @Test fun expired() = assertEquals(Level.EXPIRED, SubscriptionHealth.assess(acc(expSec = now / 1000 - 60), now).level)
    @Test fun bannedStatusIsAProblem() = assertEquals(Level.PROBLEM, SubscriptionHealth.assess(acc(status = "Banned"), now).level)
    @Test fun noDateButActive() = assertEquals(Level.OK, SubscriptionHealth.assess(acc(expSec = null), now).level)
}
