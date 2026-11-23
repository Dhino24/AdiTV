package com.aditv.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * « Liquid Glass » façon macOS, sans flou en temps réel (indisponible avant Android 12 et coûteux sur Fire TV) :
 * voile sombre translucide pour la lisibilité, reflet clair en haut, liseré lumineux dégradé (plus vif en haut,
 * comme une arête de verre éclairée), légère lueur en bas. Tout est dessiné en une passe, sans calque hors écran.
 */
object LiquidGlass {
    /** Corps : sombre et translucide (le fond reste visible), un peu plus clair en haut. */
    fun body(strength: Float = 1f) = Brush.verticalGradient(
        listOf(Color(0xFF162433).copy(alpha = (0.42f * strength + 0.14f).coerceAtMost(0.97f)), Color(0xFF101C28).copy(alpha = (0.50f * strength + 0.14f).coerceAtMost(0.98f))),
    )
    /** Arête : blanche en haut, presque invisible au milieu, reflet discret en bas. */
    val rim = Brush.verticalGradient(
        // Mat : arête fine, à peine éclairée (pas de brillance artificielle).
        0f to Color.White.copy(alpha = 0.24f), 0.35f to Color.White.copy(alpha = 0.07f),
        0.8f to Color.White.copy(alpha = 0.05f), 1f to Color.White.copy(alpha = 0.09f),
    )
}

/** Panneau / pilule en verre. [strength] < 1 : plus transparent (barres posées sur une image). */
fun Modifier.liquidGlass(shape: Shape, strength: Float = 1f, rimWidth: Dp = 1.dp): Modifier = this
    .clip(shape)
    .background(LiquidGlass.body(strength), shape)
    .drawWithContent {
        // Reflet spéculaire : une nappe claire sur le haut, qui s'éteint avant le milieu.
        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 1f to Color.Transparent, startY = 0f, endY = size.height * 0.5f),
            topLeft = Offset.Zero, size = Size(size.width, size.height * 0.5f))
        // Lueur basse : la lumière qui traverse le verre.
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.White.copy(alpha = 0.025f), startY = size.height * 0.7f, endY = size.height),
            topLeft = Offset(0f, size.height * 0.7f), size = Size(size.width, size.height * 0.3f))
        drawContent()
    }
    .border(rimWidth, LiquidGlass.rim, shape)
