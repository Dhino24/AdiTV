package com.aditv.playback

import com.aditv.playback.FrameRateMatch.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateMatchTest {
    private val uhd60 = Mode(1, 3840, 2160, 60f)
    private val modes = listOf(
        uhd60, Mode(2, 3840, 2160, 50f), Mode(3, 3840, 2160, 24f), Mode(4, 3840, 2160, 23.976f), Mode(5, 3840, 2160, 59.94f),
        Mode(6, 1920, 1080, 50f), Mode(7, 3840, 2160, 30f), Mode(8, 3840, 2160, 25f),
    )

    @Test fun picksTheRightRefreshRate() {
        assertEquals("match européen 25 i/s → 50 Hz", 2, FrameRateMatch.pick(modes, uhd60, 25f)?.id)
        assertEquals("50 i/s → 50 Hz", 2, FrameRateMatch.pick(modes, uhd60, 50f)?.id)
        assertEquals("film 24 → 24 Hz", 3, FrameRateMatch.pick(modes, uhd60, 24f)?.id)
        assertEquals("film 23,976 → 23,976 Hz", 4, FrameRateMatch.pick(modes, uhd60, 23.976f)?.id)
        assertNull("30 i/s : 60 Hz convient déjà", FrameRateMatch.pick(modes, uhd60, 30f))
        assertEquals("59,94 → 59,94 Hz", 5, FrameRateMatch.pick(modes, uhd60, 59.94f)?.id)
        assertNull("même définition seulement, cadence absurde ignorée", FrameRateMatch.pick(modes, uhd60, 17f))
        assertNull("déjà en 50 Hz", FrameRateMatch.pick(modes, Mode(2, 3840, 2160, 50f), 25f))
    }

    @Test fun estimatesFromFrameTimestamps() {
        assertEquals(25f, FrameRateMatch.estimate((0 until 40).map { it * 40_000L })!!, 0.01f)
        assertEquals(50f, FrameRateMatch.estimate((0 until 40).map { it * 20_000L }.shuffled())!!, 0.01f)
        assertEquals(23.976f, FrameRateMatch.estimate((0 until 40).map { (it * 41_708.3).toLong() })!!, 0.01f)
        assertNull("trop peu d'images", FrameRateMatch.estimate(listOf(0L, 40_000L)))
    }
}
