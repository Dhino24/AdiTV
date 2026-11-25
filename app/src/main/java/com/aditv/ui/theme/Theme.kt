package com.aditv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
/** Palette d'un thème. */
data class Palette(
    val bg: Color, val bgAlt: Color, val surface1: Color, val surface2: Color, val surface3: Color,
    /** Halo du fond (haut droite) et reflet (bas gauche) ; transparents en OLED. */
    val glow: Color, val glow2: Color,
)

/** Thèmes : « Cinéma » (charte, noir bleuté) et « Nuit OLED » (noir pur, surfaces très sombres, aucun halo). */
enum class ThemeKind(val label: String, val palette: Palette) {
    CINEMA("Cinéma", Palette(Color(0xFF07111A), Color(0xFF0B1520), Color(0xFF101C28), Color(0xFF162433), Color(0xFF1B2B3B), Color(0xFF1A3A5A), Color(0xFF0F2C3C))),
    OLED("Nuit OLED (noir pur)", Palette(Color(0xFF000000), Color(0xFF000000), Color(0xFF0A0D11), Color(0xFF11151A), Color(0xFF181D23), Color.Transparent, Color.Transparent)),
}

object Aditv {
    // Charte « rendu premium » (docs/ADI_TV_CHARTE_COULEURS.md) : noir bleuté profond, turquoise rare, blanc cassé.
    /** Thème actif (état Compose : changer de thème redessine toute l'appli). */
    var theme by mutableStateOf(ThemeKind.CINEMA)
    private val p get() = theme.palette
    val Bg get() = p.bg
    val BgAlt get() = p.bgAlt
    val Surface1 get() = p.surface1
    val Surface2 get() = p.surface2
    val Surface3 get() = p.surface3
    val Glow get() = p.glow
    val Glow2 get() = p.glow2
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

private fun scheme() = darkColorScheme(
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
fun AditvTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme(), typography = typography, content = content)
