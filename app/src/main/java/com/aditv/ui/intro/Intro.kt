package com.aditv.ui.intro

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aditv.R
import com.aditv.ui.theme.Aditv
import kotlin.math.max

/** Ouverture : au démarrage à froid seulement, une fois par processus. */
object IntroGate {
    @Volatile var played = false
    /** Les tests d'interface la désactivent pour piloter l'application immédiatement. */
    @Volatile var enabled = true
    const val DURATION_MS = 2_400
}

private fun seg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun ease(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

/**
 * Logo ADITV animé (2,4 s) : apparition du symbole, dévoilement du nom, reflet sur le « A », fondu.
 * N'attend jamais le réseau (l'application se charge dessous) ; n'importe quelle touche la passe.
 * Image et son sont désactivables séparément (Réglages) ; animations réduites : logo fixe, plus court.
 */
@Composable
fun IntroOverlay(image: Boolean, sound: Boolean, reducedMotion: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val progress = remember { Animatable(if (reducedMotion) 0.6f else 0f) }
    LaunchedEffect(Unit) {
        val player = if (sound) runCatching { MediaPlayer.create(ctx, R.raw.signature)?.apply { setVolume(0.8f, 0.8f); start() } }.getOrNull() else null
        try {
            if (image) progress.animateTo(1f, tween(if (reducedMotion) 700 else IntroGate.DURATION_MS, easing = LinearEasing))
            else kotlinx.coroutines.delay(if (sound) 600 else 0)
        } finally {
            onDone()
            // Le son se termine seul (2,4 s) puis libère le lecteur.
            player?.setOnCompletionListener { it.release() }
        }
    }
    if (!image) return
    val p = progress.value
    val sym = ease(seg(p, 0f, 0.22f))
    val word = ease(seg(p, 0.26f, 0.58f))
    val shine = seg(p, 0.44f, 0.8f)
    val outro = seg(p, 0.82f, 1f)
    val glow = if (p < 0.3f) seg(p, 0f, 0.3f) else 1f - 0.2f * seg(p, 0.3f, 0.85f) - 0.8f * seg(p, 0.85f, 1f)
    Box(Modifier.fillMaxSize().background(Color(0xFF030507)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(450.dp).graphicsLayer { alpha = max(0f, glow) }.background(Brush.radialGradient(listOf(Aditv.Accent.copy(alpha = 0.16f), Color.Transparent))))
        Row(
            Modifier.graphicsLayer { alpha = 1f - outro; scaleX = 1f + 0.03f * outro; scaleY = 1f + 0.03f * outro },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.logo_a), contentDescription = "ADITV",
                modifier = Modifier.height(90.dp)
                    .graphicsLayer { alpha = sym; scaleX = 0.86f + 0.14f * sym; scaleY = 0.86f + 0.14f * sym; compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (shine in 0.01f..0.99f) {
                            val x = size.width * (1.3f - 1.6f * shine)
                            drawRect(
                                Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent), start = Offset(x - 40.dp.toPx(), 0f), end = Offset(x + 40.dp.toPx(), size.height)),
                                blendMode = BlendMode.SrcAtop,
                            )
                        }
                    },
            )
            Spacer(Modifier.width(9.dp))
            Box(Modifier.width(312.dp * word).clipToBounds()) {
                Image(
                    painterResource(R.drawable.logo_word), contentDescription = null, contentScale = ContentScale.FillHeight, alignment = Alignment.CenterStart,
                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).requiredWidth(312.dp).height(90.dp),
                )
            }
        }
    }
}
