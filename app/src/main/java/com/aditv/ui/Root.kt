package com.aditv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditv.R
import com.aditv.data.Session
import com.aditv.di.AppContainer
import com.aditv.ui.login.LoginScreen
import com.aditv.ui.shell.Shell
import com.aditv.ui.theme.Aditv
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer absent") }

/** Textes agrandis pour un téléviseur regardé à plusieurs mètres (demande d'Adama : « Grand », environ +30 %). */
const val BASE_TEXT_SCALE = 1.3f

/** Élément principal de l'écran courant : cible du focus quand on valide une rubrique du menu. */
val LocalScreenEntry = staticCompositionLocalOf { androidx.compose.ui.focus.FocusRequester() }

@Composable
fun Root() {
    val c = LocalContainer.current
    val session by c.accounts.session.collectAsStateWithLifecycle()
    val reducedPref by c.prefs.reduceMotionPref.collectAsStateWithLifecycle(false)
    val reduced = com.aditv.ui.design.systemReducedMotion() || reducedPref
    val textScale by c.prefs.textScalePercent.collectAsStateWithLifecycle(100)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val navSound by c.prefs.navSoundEnabled.collectAsStateWithLifecycle(true)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(navSound) { com.aditv.ui.common.NavSound.init(ctx); com.aditv.ui.common.NavSound.enabled = navSound }
    // Ouverture au démarrage à froid : préférences locales lues sans attendre le réseau.
    var intro by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var showIntro by remember { mutableStateOf(com.aditv.ui.intro.IntroGate.enabled && !com.aditv.ui.intro.IntroGate.played) }
    LaunchedEffect(Unit) {
        if (showIntro) intro = c.prefs.introImageEnabled.first() to c.prefs.introSoundEnabled.first()
    }
    fun endIntro() { com.aditv.ui.intro.IntroGate.played = true; showIntro = false }
    CompositionLocalProvider(
        com.aditv.ui.design.LocalReducedMotion provides reduced,
        // Accessibilité : taille du texte choisie dans Réglages (agrandit les textes, pas la mise en page).
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * BASE_TEXT_SCALE * textScale / 100f),
    ) {
        Box(Modifier.fillMaxSize().background(Aditv.Bg).onPreviewKeyEvent {
            if (showIntro) { if (it.type == KeyEventType.KeyDown) endIntro(); true }
            else {
                // Validation (OK) : petit son de sélection, sans bloquer la touche.
                if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0 &&
                    it.nativeKeyEvent.keyCode in intArrayOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) com.aditv.ui.common.NavSound.selected()
                false
            }
        }) {
            when (val s = session) {
                Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.logo_full), contentDescription = "ADI TV", modifier = Modifier.width(220.dp).aspectRatio(com.aditv.ui.intro.LOGO_RATIO))
                }
                is Session.SignedOut -> LoginScreen(s.notice)
                is Session.Active -> Shell(s)
            }
            val prefs = intro
            if (showIntro && prefs != null) com.aditv.ui.intro.IntroOverlay(prefs.first, prefs.second, reduced) { endIntro() }
            else if (showIntro) Box(Modifier.fillMaxSize().background(Color(0xFF030507)))
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)
private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRANCE)

/** Dates affichées dans le fuseau de l'appareil, fuseau indiqué. */
fun formatEpochDate(sec: Long): String {
    val z = ZoneId.systemDefault()
    return dateFmt.format(Instant.ofEpochSecond(sec).atZone(z)) + " (" + z.id + ")"
}

fun formatMillisDateTime(ms: Long): String = dateTimeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
