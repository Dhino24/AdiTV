package com.aditv.ui.saver

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.flow.first
import android.view.WindowManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.aspectRatio
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.design.LocalReducedMotion
import com.aditv.ui.design.premiumBackground
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Écran de veille cinéma : grands visuels du catalogue en fondu (léger zoom lent), titre, heure et date, météo de la
 * ville choisie et prochain match des équipes suivies.
 * Toute touche le ferme (sans autre effet). L'écran reste allumé au plus 60 min, puis la Fire TV reprend la main.
 */
@Composable
fun ScreenSaver(accountId: String, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val reduced = LocalReducedMotion.current
    val activity = androidx.activity.compose.LocalActivity.current
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LaunchedEffect(Unit) { delay(60 * 60_000L); activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    val cards by produceState(emptyList<com.aditv.data.db.CatalogCard>()) { value = runCatching { c.db.metadata().randomBackdrops(accountId, 30) }.getOrDefault(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(cards) { while (cards.size > 1) { delay(9_000); index = (index + 1) % cards.size } }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(10_000); value = System.currentTimeMillis() } }
    // Météo de la ville choisie et prochain match des équipes suivies (cache FootAdi, aucune requête en plus).
    val weather by produceState<com.aditv.data.weather.WeatherNow?>(null) {
        while (true) {
            val city = c.prefs.weatherCity.first()
            value = if (city.isBlank()) null else c.weather.now(city) ?: value
            delay(30 * 60_000L)
        }
    }
    val nextMatch by produceState<com.aditv.data.football.Fixture?>(null) {
        while (true) {
            value = if (c.footballKey.get() == null) null else runCatching {
                (0..1).flatMap { d -> c.football.fixtures(d) }
                    .filter { com.aditv.data.football.FootballRepository.isFollowed(it) && !it.finished && (it.live || it.kickoff > System.currentTimeMillis()) }
                    .minByOrNull { if (it.live) 0L else it.kickoff }
            }.getOrNull()
            delay(5 * 60_000L)
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().premiumBackground().focusRequester(focus).focusable()
            .onPreviewKeyEvent { onDismiss(); true },
    ) {
        val card = cards.getOrNull(index)
        Crossfade(card, animationSpec = tween(1_600), label = "veille") { k ->
            if (k != null) {
                val zoom = if (reduced) 1f else rememberInfiniteTransition(label = "zoom").animateFloat(1f, 1.08f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "z").value
                Box(Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }) {
                    RemoteImage(tmdbImage(k.tmdbBackdrop, "w1280"), k.title, Modifier.fillMaxSize(), fallbackTitle = false)
                }
            }
        }
        // Voile bas pour la lisibilité.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp)) {
            Image(painterResource(R.drawable.logo_full), contentDescription = "ADI TV", modifier = Modifier.height(18.dp).aspectRatio(com.aditv.ui.intro.LOGO_RATIO))
            card?.let { Text(it.title, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Aditv.Text, modifier = Modifier.padding(top = 10.dp)) }
        }
        nextMatch?.let { f ->
            val today = java.time.LocalDate.now().toString() == SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date(f.kickoff))
            val whenTxt = when {
                f.live -> "EN DIRECT ${f.homeGoals ?: 0} – ${f.awayGoals ?: 0}" + (f.elapsed?.let { " · $it'" } ?: "")
                today -> (if (SimpleDateFormat("H", Locale.FRANCE).format(Date(f.kickoff)).toInt() >= 18) "ce soir " else "aujourd'hui ") + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(f.kickoff))
                else -> "demain " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(f.kickoff))
            }
            Row(Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 40.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (f.live) "●" else "⚽", fontSize = 14.sp, color = if (f.live) Aditv.Live else Aditv.Text)
                Text("  ${f.home.name} – ${f.away.name}", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Aditv.Text)
                Text("  ·  $whenTxt", fontSize = 14.sp, color = if (f.live) Aditv.Live else Aditv.Accent)
            }
        }
        weather?.let { w ->
            Column(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 36.dp), horizontalAlignment = Alignment.End) {
                Text("${w.icon}  ${w.tempC}°", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Aditv.Text)
                Text(w.city + " · " + w.label + (if (w.minC != null && w.maxC != null) " · ${w.minC}° / ${w.maxC}°" else ""), fontSize = 13.sp, color = Aditv.TextSecondary)
            }
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 36.dp), horizontalAlignment = Alignment.End) {
            Text(SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(now)), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, color = Aditv.Text)
            Text(SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date(now)).replaceFirstChar { it.uppercase() }, fontSize = 13.sp, color = Aditv.TextSecondary)
        }
    }
}
