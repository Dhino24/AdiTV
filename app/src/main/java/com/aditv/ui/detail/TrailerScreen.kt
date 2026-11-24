package com.aditv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.ui.common.TrailerControl
import com.aditv.ui.common.TrailerEmbed
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.delay

/**
 * Bande-annonce en plein écran dans ADI TV (lecteur YouTube officiel). OK : pause / lecture ; Retour : fiche.
 * Si la vidéo refuse l'intégration, l'application YouTube prend le relais (quand elle est installée).
 */
@Composable
fun TrailerScreen(videoId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val control = remember { TrailerControl() }
    val focus = remember { FocusRequester() }
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() }; delay(4_000); hint = false }
    BackHandler { onClose() }
    Box(
        Modifier.fillMaxSize().focusRequester(focus).focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.MediaPlayPause)) { control.toggle(); hint = true; true } else false
            },
    ) {
        TrailerEmbed(videoId, muted = false, loop = false, modifier = Modifier.fillMaxSize(), control = control,
            onEnded = onClose,
            onError = { if (com.aditv.ui.common.Trailer.canOpen(context)) com.aditv.ui.common.Trailer.open(context, videoId); onClose() })
        if (hint) Text("OK : pause / lecture · Retour : revenir à la fiche", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).background(Color(0xB0000000)).padding(horizontal = 12.dp, vertical = 5.dp))
    }
}
