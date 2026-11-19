package com.aditv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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

/** Élément principal de l'écran courant : cible du focus quand on valide une rubrique du menu. */
val LocalScreenEntry = staticCompositionLocalOf { androidx.compose.ui.focus.FocusRequester() }

@Composable
fun Root() {
    val session by LocalContainer.current.accounts.session.collectAsStateWithLifecycle()
    when (val s = session) {
        Session.Loading -> Box(Modifier.fillMaxSize().background(Aditv.Bg), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.logo_full), contentDescription = "ADITV", modifier = Modifier.width(320.dp))
        }
        is Session.SignedOut -> LoginScreen(s.notice)
        is Session.Active -> Shell(s)
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
