package com.aditv.ui.intro

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aditv.R

/** Ouverture : au démarrage à froid seulement, une fois par processus. */
object IntroGate {
    @Volatile var played = false
    /** Les tests d'interface la désactivent pour piloter l'application immédiatement. */
    @Volatile var enabled = true
    /** Durée du son d'ouverture (4,36 s) : l'animation dure exactement autant que lui. */
    const val DURATION_MS = 4_360
    /** Durée réellement utilisée au dernier lancement (diagnostic). */
    @Volatile var lastDurationMs = 0
}

private fun seg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeInOut(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2

/** Repères du son (en part de sa durée) : montée jusqu'à l'impact (≈ 1,25 s), tenue, extinction finale (≈ 3,8 s). */
private const val HIT = 0.285f
private const val OUTRO = 0.865f
private val Gold = Color(0xFFF2B33D)

/**
 * Logo ADI TV animé sur le son d'ouverture, les deux se terminent ensemble avant l'apparition du menu :
 * montée (halo doré qui grandit, logo en approche), impact (le logo se pose avec un éclat), reflet qui le traverse,
 * légère avancée, puis extinction synchronisée avec la fin du son. L'animation démarre au moment exact où le son part.
 * N'attend jamais le réseau (l'application se charge dessous) ; n'importe quelle touche la passe et coupe le son.
 * Image et son sont désactivables séparément (Réglages) ; animations réduites : simple fondu.
 */
@Composable
fun IntroOverlay(image: Boolean, sound: Boolean, reducedMotion: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Son préparé d'abord : l'animation part au même instant que lui et dure exactement autant.
        val player = if (sound) runCatching { MediaPlayer.create(ctx, R.raw.intro_sound)?.apply { setVolume(1f, 1f) } }.getOrNull() else null
        val duration = player?.duration?.takeIf { it in 1_000..10_000 } ?: if (image) IntroGate.DURATION_MS else 0
        IntroGate.lastDurationMs = duration
        com.aditv.core.CrashReporter.event("Ouverture : ${duration} ms" + if (player == null && sound) " (son indisponible)" else "")
        var finished = false
        try {
            player?.start()
            if (image || player != null) progress.animateTo(1f, tween(duration, easing = LinearEasing))
            finished = true
        } finally {
            // Passée avant la fin (touche) : le son s'arrête avec l'image.
            if (!finished) runCatching { player?.stop() }
            runCatching { player?.release() }
            onDone()
        }
    }
    if (!image) return
    val p = progress.value
    if (reducedMotion) {
        val a = seg(p, 0f, 0.15f) * (1f - seg(p, OUTRO, 1f))
        Box(Modifier.fillMaxSize().background(Color(0xFF030507)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.logo_full), contentDescription = "ADI TV", modifier = Modifier.width(620.dp).graphicsLayer { alpha = a })
        }
        return
    }
    // Montée : le logo approche, encore discret, pendant que le halo grandit.
    val approach = easeOut(seg(p, 0.04f, HIT))
    // Impact : pose avec un léger dépassement d'échelle, éclat bref.
    val impact = seg(p, HIT, HIT + 0.07f)
    val settle = easeOut(seg(p, HIT + 0.07f, HIT + 0.16f))
    val flash = if (p < HIT) 0f else (1f - seg(p, HIT, HIT + 0.12f))
    val shine = seg(p, 0.40f, 0.64f)
    val push = easeInOut(seg(p, HIT + 0.16f, OUTRO))
    val outro = easeInOut(seg(p, OUTRO, 1f))

    val logoAlpha = (if (p < HIT) 0.35f * approach else 0.35f + 0.65f * easeOut(impact)) * (1f - outro)
    val scale = when {
        p < HIT -> 0.72f + 0.16f * approach
        p < HIT + 0.07f -> 0.88f + 0.18f * easeOut(impact)          // jusqu'à 1,06
        else -> 1.06f - 0.06f * settle + 0.035f * push                // retour à 1, puis lente avancée
    } + 0.02f * outro
    val halo = (0.25f + 0.55f * approach + 0.6f * flash).coerceAtMost(1f) * (1f - outro)

    Box(Modifier.fillMaxSize().background(Color(0xFF030507)), contentAlignment = Alignment.Center) {
        // Halo doré derrière le logo.
        Box(Modifier.size(width = 980.dp, height = 560.dp).graphicsLayer { alpha = halo; scaleX = 0.8f + 0.25f * approach; scaleY = 0.8f + 0.25f * approach }
            .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.30f), Gold.copy(alpha = 0.08f), Color.Transparent))))
        Image(
            painterResource(R.drawable.logo_full), contentDescription = "ADI TV",
            modifier = Modifier.width(640.dp)
                .graphicsLayer { alpha = logoAlpha; scaleX = scale; scaleY = scale; compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Éclat de l'impact, puis reflet lumineux qui traverse le logo (limité au logo lui-même).
                    if (flash > 0.01f) drawRect(Color.White.copy(alpha = 0.55f * flash), blendMode = BlendMode.SrcAtop)
                    if (shine in 0.01f..0.99f) {
                        val x = size.width * (-0.25f + 1.5f * easeInOut(shine))
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                                start = Offset(x - 70.dp.toPx(), 0f), end = Offset(x + 70.dp.toPx(), size.height),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
        )
    }
}
