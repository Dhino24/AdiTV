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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    /** Durée du son d'ouverture (4,39 s) : l'animation dure exactement autant que lui. */
    const val DURATION_MS = 4_390
    /** Durée réellement utilisée au dernier lancement (diagnostic). */
    @Volatile var lastDurationMs = 0
}

/** Proportions du logo ADI TV (cadre du SVG du pack : 1150 × 291). */
const val LOGO_RATIO = 1150f / 291f

private fun seg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeInOut(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).let { it * it * it } / 2

/**
 * Repères (part de la durée du son, 4,39 s) — scénario du pack ADI TV (ANIMATION_INTRO.md) étiré sur le son :
 * souffle (0–0,25 s), ADI se révèle avec le play (→ 1,25 s, temps fort du son), TV se révèle, les trois traits
 * vert / jaune / rouge apparaissent discrètement, stabilisation sur le logo net, fondu terminé avec le son.
 */
private const val BREATH = 0.057f
private const val HIT = 0.285f
private const val TV_END = 0.40f
private const val BARS_START = 0.37f
private const val BARS_END = 0.48f
private const val OUTRO = 0.865f
private val IntroBg = Color(0xFF050505)

/**
 * Ouverture ADI TV calée sur le son : démarre au même instant que lui, se termine avec lui, puis le menu.
 * Sobre (charte : ni étincelles ni explosion). N'attend jamais le réseau ; une touche la passe et coupe le son.
 * Image et son désactivables (Réglages) ; animations réduites : logo fixe, simple fondu final.
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
    val outro = easeInOut(seg(p, OUTRO, 1f))
    val logoModifier = Modifier.width(680.dp).aspectRatio(LOGO_RATIO)

    Box(Modifier.fillMaxSize().background(IntroBg), contentAlignment = Alignment.Center) {
        if (reducedMotion) {
            Image(painterResource(R.drawable.logo_full), contentDescription = "ADI TV", modifier = logoModifier.graphicsLayer { alpha = 1f - outro })
            return@Box
        }
        // Souffle : lueur très douce au centre, puis reflet au sol sous le logo une fois posé.
        val breath = seg(p, 0f, BREATH)
        val settled = seg(p, BARS_END, BARS_END + 0.1f)
        Box(Modifier.size(width = 900.dp, height = 420.dp).graphicsLayer { alpha = (0.5f * breath + 0.3f * settled) * (1f - outro) }
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent))))
        Box(Modifier.padding(top = 230.dp).size(width = 640.dp, height = 18.dp).graphicsLayer { alpha = settled * (1f - outro) }
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))))

        // Légère avancée continue jusqu'au fondu (stabilisation, sans à-coup).
        val push = 0.97f + 0.03f * easeInOut(seg(p, BREATH, OUTRO)) + 0.015f * outro
        Box(logoModifier.graphicsLayer { scaleX = push; scaleY = push; alpha = 1f - outro }) {
            // ADI : se révèle de gauche à droite (souffle → temps fort).
            val adi = easeInOut(seg(p, BREATH, HIT))
            Image(
                painterResource(R.drawable.logo_adi), contentDescription = "ADI TV",
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f + 0.65f * adi }
                    // Le mot ADI occupe les 72 % gauches du logo : dévoilé jusqu'à son bord droit.
                    .drawWithContent { clipRect(right = size.width * 0.74f * adi) { this@drawWithContent.drawContent() } },
            )
            // Play rouge : apparaît dans le A et se pose sur le temps fort du son.
            val playIn = easeOut(seg(p, HIT - 0.07f, HIT))
            val playPop = seg(p, HIT, HIT + 0.06f)
            val playScale = if (p < HIT) 0.6f + 0.5f * playIn else 1.1f - 0.1f * easeOut(playPop)
            Image(
                painterResource(R.drawable.logo_play), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = playIn
                    // Mise à l'échelle autour du triangle (≈ 20 % de la largeur, 70 % de la hauteur du logo).
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.20f, 0.70f)
                    scaleX = playScale; scaleY = playScale
                },
            )
            // TV : glisse doucement depuis la droite.
            val tv = easeOut(seg(p, HIT, TV_END))
            Image(
                painterResource(R.drawable.logo_tv), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = tv; translationX = (1f - tv) * 40.dp.toPx() },
            )
            // Trois traits vert / jaune / rouge : discrets, de gauche à droite.
            val bars = seg(p, BARS_START, BARS_END)
            Image(
                painterResource(R.drawable.logo_bars), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = easeOut(bars) }
                    .drawWithContent { clipRect(left = size.width * 0.74f, right = size.width * (0.74f + 0.20f * easeOut(bars))) { this@drawWithContent.drawContent() } },
            )
        }
    }
}
