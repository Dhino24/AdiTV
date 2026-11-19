package com.aditv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** Charte ADITV (dossier 02_Design/01_CHARTE_ET_MOUVEMENT.md). Valeurs ajustables après contrôle de contraste sur TV. */
object Aditv {
    val Bg = Color(0xFF090D12)
    val Surface1 = Color(0xFF111922)
    val Surface2 = Color(0xFF18232E)
    val Text = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFFADB8C5)
    val Accent = Color(0xFF00D7D7)
    val Line = Color(0xFF26333E)
    val Ok = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFBE50)
    val Error = Color(0xFFFF6B7A)
    val OnLight = Color(0xFF071014)
}

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

// Tailles de départ « canapé » : à valider sur le téléviseur (texte 16–20 sp, titres 28–40 sp).
private val typography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

@Composable
fun AditvTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = typography, content = content)
