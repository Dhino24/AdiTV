package com.aditv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.aditv.R

/**
 * Charte ADI TV « rendu premium » : noir bleuté profond, turquoise rare, blanc cassé élégant.
 * Le prototype est dessiné à 1920 × 1080 px ; la Fire TV affiche 960 × 540 dp : 1 dp = 2 px du prototype.
 */
object Aditv {
    // Charte « rendu premium » (docs/ADI_TV_CHARTE_COULEURS.md) : noir bleuté profond, turquoise rare, blanc cassé.
    val Bg = Color(0xFF07111A)
    val BgAlt = Color(0xFF0B1520)
    val Surface1 = Color(0xFF101C28)
    val Surface2 = Color(0xFF162433)
    val Surface3 = Color(0xFF1B2B3B)
    val Text = Color(0xFFF4F7FA)
    val TextSecondary = Color(0xFFC3CED8)
    val TextTertiary = Color(0xFF7F93A6)
    /** Focus, sélection, progression, interactions actives — jamais en aplat sur de grandes zones. */
    val Accent = Color(0xFF22D3C5)
    /** Halo très discret et détails de focus. */
    val AccentSoft = Color(0xFF7EE7F2)
    /** Badges « EN DIRECT » et alertes pertinentes. */
    val Live = Color(0xFFFF6B57)
    val Line = Color(0x1AFFFFFF)
    val Ok = Color(0xFF26C281)
    val Warn = Color(0xFFF6D58A)
    val Error = Color(0xFFFF8E80)
    /** Or discret : usage très rare (badge exceptionnel). */
    val Gold = Color(0xFFE0B75F)
    val OnLight = Color(0xFF07111A)
}

/** Outfit (titres, géométrique comme le logo) et Inter (texte). Polices sous licence OFL, embarquées. */
val Outfit = FontFamily(Font(R.font.outfit_medium, FontWeight.Medium), Font(R.font.outfit_semibold, FontWeight.SemiBold))
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val scheme = darkColorScheme(
    primary = Aditv.Accent,
    onPrimary = Aditv.OnLight,
    secondary = Aditv.Accent,
    background = Aditv.Bg,
    onBackground = Aditv.Text,
    surface = Aditv.Surface1,
    onSurface = Aditv.Text,
    surfaceVariant = Aditv.Surface2,
    onSurfaceVariant = Aditv.TextSecondary,
    inverseSurface = Aditv.Text,
    inverseOnSurface = Aditv.OnLight,
    border = Aditv.Line,
    error = Aditv.Error,
)

private fun inter(size: Int, w: FontWeight = FontWeight.Normal, lh: Int = (size * 1.4f).toInt()) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = w, lineHeight = lh.sp)
private fun outfit(size: Int, w: FontWeight = FontWeight.SemiBold, lh: Int = (size * 1.1f).toInt()) =
    TextStyle(fontFamily = Outfit, fontSize = size.sp, fontWeight = w, lineHeight = lh.sp)

private val typography = Typography(
    displayLarge = outfit(48, lh = 46),
    displayMedium = outfit(40, lh = 40),
    displaySmall = outfit(32, lh = 34),
    headlineLarge = outfit(26),
    headlineMedium = outfit(25),
    headlineSmall = outfit(20),
    titleLarge = outfit(15, FontWeight.Medium, 18),
    titleMedium = inter(11, FontWeight.SemiBold, 14),
    titleSmall = inter(10, FontWeight.SemiBold, 13),
    bodyLarge = inter(12, lh = 18),
    bodyMedium = inter(11, lh = 16),
    bodySmall = inter(9, lh = 12),
    labelLarge = inter(11, FontWeight.SemiBold),
    labelMedium = inter(9, FontWeight.Medium),
    labelSmall = inter(8, FontWeight.SemiBold).copy(letterSpacing = 2.4.sp),
)

@Composable
fun AditvTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = typography, content = content)
