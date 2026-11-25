package com.aditv.ui.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.aditv.ui.theme.Aditv

/**
 * Fond « premium » (charte) : noir bleuté profond, lumière bleu nuit diffuse en haut à droite, reflet plus discret
 * en bas à gauche, vignettage léger. Profond et lumineux sans aplat bleu vif ; dessiné en une passe (aucune image).
 */
fun Modifier.premiumBackground(): Modifier = this.drawBehind {
    drawRect(Aditv.Bg)
    val w = size.width; val h = size.height
    // Halo principal : bleu nuit lumineux, haut droite.
    if (Aditv.Glow.alpha == 0f) return@drawBehind // Nuit OLED : noir pur, sans halo
    drawRect(Brush.radialGradient(listOf(Aditv.Glow.copy(alpha = 0.85f), Aditv.Glow.copy(alpha = 0.4f), Color.Transparent),
        center = Offset(w * 0.86f, -h * 0.12f), radius = w * 0.95f))
    // Reflet secondaire : bas gauche, plus froid.
    drawRect(Brush.radialGradient(listOf(Aditv.Glow2.copy(alpha = 0.7f), Color.Transparent),
        center = Offset(w * 0.04f, h * 1.08f), radius = w * 0.6f))
    // Pointe de turquoise presque invisible : signature ADI TV.
    drawRect(Brush.radialGradient(listOf(Aditv.Accent.copy(alpha = 0.045f), Color.Transparent),
        center = Offset(w * 0.62f, h * 0.18f), radius = w * 0.45f))
    // Vignettage : bords légèrement assombris, le centre respire.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
        center = Offset(w * 0.5f, h * 0.45f), radius = w * 0.85f))
}
