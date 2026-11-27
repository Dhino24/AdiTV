package com.aditv.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.aditv.playback.engine.DelayAudioProcessor
import com.aditv.ui.player.ChannelHistory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioDelayAndHistoryTest {
    private fun pcm(frames: Int) = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder()).apply { repeat(frames * 4) { put(1) }; flip() }

    @Test fun delayInsertsSilenceThenDropsWhenReduced() {
        val p = DelayAudioProcessor()
        p.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)); p.flush()
        p.delayMs = 100 // 4 800 trames
        p.queueInput(pcm(480))
        val out = p.output
        assertEquals((480 + 4_800) * 4, out.remaining())
        assertEquals(0.toByte(), out.get(0))
        p.delayMs = 0 // on retire le retard : 4 800 trames sautées
        p.queueInput(pcm(10_000))
        assertEquals((10_000 - 4_800) * 4, p.output.remaining())
    }

    @Test fun previousChannelIgnoresVersionsOfTheSameChannel() {
        ChannelHistory.reset()
        ChannelHistory.record("tf1-hd", "tf1", "TF1 HD")
        assertNull(ChannelHistory.previous)
        ChannelHistory.record("tf1-fhd", "tf1", "TF1 FHD") // secours : même chaîne
        assertNull(ChannelHistory.previous)
        ChannelHistory.record("m6", "m6", "M6")
        assertEquals("tf1-fhd", ChannelHistory.previous?.id)
    }
}
