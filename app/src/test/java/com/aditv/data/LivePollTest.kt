package com.aditv.data

import com.aditv.data.football.FootballRepository.Companion.pollInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePollTest {
    @Test fun oneMatchWithFullQuotaIsAboutEveryMinuteAndHalf() {
        val ms = pollInterval(remaining = 90, minutesLeft = 110)
        assertTrue(ms in 60_000L..100_000L)
    }
    @Test fun neverFasterThanOneMinute() = assertEquals(60_000L, pollInterval(100, 20))
    @Test fun lowQuotaFallsBackToFiveMinutes() = assertEquals(5 * 60_000L, pollInterval(15, 110))
    @Test fun nothingLeftToPlay() = assertEquals(5 * 60_000L, pollInterval(90, 0))
}
