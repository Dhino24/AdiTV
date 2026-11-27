package com.aditv.playback.engine

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import java.nio.ByteBuffer

/**
 * Décalage du son pour Media3 : retarde le son de [delayMs] (silence inséré au début et après chaque saut ; un
 * changement en cours de lecture ajoute du silence ou saute des échantillons). Media3 ne sait pas AVANCER le son :
 * une valeur négative passe par VLC. Sans effet sur le son « passthrough » (Dolby envoyé tel quel à la TV).
 */
@OptIn(UnstableApi::class)
class DelayAudioProcessor : BaseAudioProcessor() {
    @Volatile var delayMs: Int = 0
    private var appliedFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (Util.isEncodingLinearPcm(inputAudioFormat.encoding)) inputAudioFormat else AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val fmt = inputAudioFormat
        val frameBytes = fmt.bytesPerFrame
        if (frameBytes <= 0) { replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip(); return }
        val target = delayMs.coerceAtLeast(0).toLong() * fmt.sampleRate / 1000
        var diff = target - appliedFrames
        if (diff < 0) {
            val dropFrames = minOf(-diff, (inputBuffer.remaining() / frameBytes).toLong())
            inputBuffer.position(inputBuffer.position() + (dropFrames * frameBytes).toInt())
            appliedFrames -= dropFrames
            diff = 0
        }
        // Au plus 1 s de silence par paquet (le reste au paquet suivant).
        val silenceFrames = minOf(diff, fmt.sampleRate.toLong())
        val silenceBytes = (silenceFrames * frameBytes).toInt()
        val out = replaceOutputBuffer(silenceBytes + inputBuffer.remaining())
        repeat(silenceBytes) { out.put(0) }
        appliedFrames += silenceFrames
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() { appliedFrames = 0 }
    override fun onReset() { appliedFrames = 0 }
}

/** Un seul lecteur Media3 à la fois : un seul processeur partagé, réglé à chaque ouverture. */
object AudioDelay {
    val processor = DelayAudioProcessor()
    /** Valeurs proposées (ms). Négatif = son avancé (VLC). */
    val STEPS = listOf(-300, -200, -100, 0, 100, 200, 300, 500, 800)
    fun label(ms: Int) = when { ms == 0 -> "Aucun"; ms > 0 -> "Son retardé de $ms ms"; else -> "Son avancé de ${-ms} ms" }
}
