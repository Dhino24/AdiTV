package com.aditv.playback

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class PlaybackControllerTest {
    private fun req(url: String, live: Boolean = false) = PlaybackRequest(url, "T", null, live, 42_000, "c", "MOVIE", null)

    @Test fun onlyOneSessionAtATime() {
        val c = PlaybackController(ApplicationProvider.getApplicationContext(), OkHttpClient())
        val first = c.open(req("http://h.invalid/movie/u/p/1.mp4"))
        assertEquals(1, c.activeSessions.value)
        val second = c.open(req("http://h.invalid/live/u/p/2.ts", live = true))
        assertNotSame(first, second)
        assertEquals("une seule session après changement de contenu", 1, c.activeSessions.value)
        c.release()
        assertEquals(0, c.activeSessions.value); assertNull(c.current)
    }

    @Test fun mimeHints() {
        assertEquals("application/x-mpegURL", PlaybackController.mimeTypeFor("http://h/live/u/p/1.m3u8?token=x"))
        assertEquals("video/mp2t", PlaybackController.mimeTypeFor("http://h/live/u/p/1.ts"))
        assertNull(PlaybackController.mimeTypeFor("http://h/movie/u/p/1.mkv"))
    }
}
