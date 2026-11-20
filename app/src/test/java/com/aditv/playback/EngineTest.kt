package com.aditv.playback

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.playback.engine.BufferPolicy
import com.aditv.playback.engine.EngineChoice
import com.aditv.playback.engine.EngineKind
import com.aditv.playback.engine.EnginePolicy
import com.aditv.playback.engine.EngineStore
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class EngineTest {
    @Test fun autoFallsBackOnceAndOnlyForFormatErrors() {
        assertEquals(EngineKind.MEDIA3, EnginePolicy.initial(EngineChoice.AUTO))
        assertEquals(EngineKind.VLC, EnginePolicy.initial(EngineChoice.VLC))
        assertTrue(EnginePolicy.shouldFallback(EngineChoice.AUTO, EngineKind.MEDIA3, formatProblem = true, alreadyFellBack = false))
        assertFalse("une seule bascule", EnginePolicy.shouldFallback(EngineChoice.AUTO, EngineKind.MEDIA3, true, alreadyFellBack = true))
        assertFalse("panne réseau : pas de bascule", EnginePolicy.shouldFallback(EngineChoice.AUTO, EngineKind.MEDIA3, formatProblem = false, alreadyFellBack = false))
        assertFalse("choix manuel respecté", EnginePolicy.shouldFallback(EngineChoice.MEDIA3, EngineKind.MEDIA3, true, false))
    }

    @Test fun turboExtendsReserveWithMemoryCap() {
        val n = BufferPolicy(false); val t = BufferPolicy(true)
        assertTrue(t.minBufferMs > n.minBufferMs && t.maxBufferMs > n.maxBufferMs)
        assertEquals(96 * 1024 * 1024, t.targetBytes)
        assertTrue(t.vlcNetworkCachingMs > n.vlcNetworkCachingMs)
    }

    @Test fun choiceIsRememberedPerContentAndSessionsAreLogged() {
        val s = EngineStore(ApplicationProvider.getApplicationContext())
        assertNull(s.choiceFor("MOVIE|x|2024"))
        s.setChoice("MOVIE|x|2024", EngineChoice.VLC)
        assertEquals(EngineChoice.VLC, s.choiceFor("MOVIE|x|2024"))
        s.setChoice("MOVIE|x|2024", EngineChoice.AUTO)
        assertNull("Auto = valeur par défaut, rien de stocké", s.choiceFor("MOVIE|x|2024"))
        s.clearLog()
        repeat(25) { i -> s.record(EngineStore.SessionLog(i.toLong(), "T$i", "VLC", false, 900, i, 2, 60, null, false)) }
        val log = s.sessions()
        assertEquals("20 sessions au plus", 20, log.size)
        assertEquals("plus récente d'abord", "T24", log.first().title)
    }

    @Test fun media3EngineIsTheOnlySession() {
        val c = PlaybackController(ApplicationProvider.getApplicationContext(), OkHttpClient())
        val req = PlaybackRequest("http://h.invalid/movie/u/p/1.mp4", "T", null, false, 1_000, "c", "MOVIE", null)
        val e1 = c.openEngine(req, null, EngineKind.MEDIA3, BufferPolicy(true), null)
        assertEquals(1, c.activeSessions.value)
        assertTrue(c.turbo.value)
        val e2 = c.openEngine(req, null, EngineKind.MEDIA3, BufferPolicy(false), null)
        assertTrue(e1 !== e2)
        assertEquals(1, c.activeSessions.value)
        assertFalse(c.turbo.value)
        c.release()
        assertEquals(0, c.activeSessions.value); assertNull(c.currentEngine)
    }
}
