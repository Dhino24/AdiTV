package com.aditv.ui.multiplex

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.BuildConfig
import com.aditv.data.Session
import com.aditv.ui.LocalContainer
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Un écran du multiplex : chaîne, lecteur, erreur éventuelle. */
private class Slot(val id: String) {
    var title by mutableStateOf("")
    var player by mutableStateOf<ExoPlayer?>(null)
    var error by mutableStateOf<String?>(null)
}

/**
 * Multiplex : 2 chaînes à l'écran. ←/→ choisit l'écran actif (liseré turquoise, c'est lui qu'on entend) ;
 * OK : « focus » — l'écran actif en grand, l'autre en vignette (OK de nouveau : retour au partage 50/50) ;
 * Retour : la chaîne active seule, en plein écran. Utilise 2 connexions de l'abonnement.
 */
@OptIn(UnstableApi::class)
@Composable
fun MultiplexScreen(session: Session.Active, firstId: String, secondId: String, onSingle: (String) -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val slots = remember { listOf(Slot(firstId), Slot(secondId)) }
    var active by remember { mutableStateOf(0) }
    var focusMode by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }

    // La session du lecteur normal est libérée avant d'ouvrir les deux flux (connexions de l'abonnement).
    LaunchedEffect(Unit) {
        c.playback.release()
        runCatching { focus.requestFocus() }
        slots.forEachIndexed { i, s ->
            if (i == 1) delay(1_200) // laisse le serveur accepter la 1re connexion
            val item = c.db.catalog().observeItem(s.id).first() ?: run { s.error = "Chaîne introuvable."; return@forEachIndexed }
            s.title = com.aditv.data.live.ChannelFamilies.cleanName(item.title)
            val url = runCatching { session.adapter.streamUrl(com.aditv.data.provider.StreamTarget.Live(item.providerId)) }.getOrNull()
                ?: run { s.error = "Flux indisponible."; return@forEachIndexed }
            val ds = OkHttpDataSource.Factory(c.http.streams).setUserAgent("ADITV/${BuildConfig.VERSION_NAME}")
            val p = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).build()
            p.setMediaItem(MediaItem.fromUri(url))
            p.volume = if (i == active) 1f else 0f
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    s.error = com.aditv.playback.PlaybackErrors.message(error).let { m ->
                        if (com.aditv.playback.PlaybackErrors.serverBusy(error)) "Votre abonnement n'autorise pas 2 chaînes en même temps." else m
                    }
                }
            })
            p.prepare(); p.playWhenReady = true
            s.player = p
        }
        delay(5_000); hint = false
    }
    DisposableEffect(Unit) { onDispose { slots.forEach { it.player?.release(); it.player = null } } }
    // Le son suit l'écran actif.
    LaunchedEffect(active) { slots.forEachIndexed { i, s -> s.player?.volume = if (i == active) 1f else 0f } }
    BackHandler { onSingle(slots[active].id) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { active = 0; hint = true; true }
                    Key.DirectionRight -> { active = 1; hint = true; true }
                    Key.DirectionCenter, Key.Enter -> { if (ev.nativeKeyEvent.repeatCount == 0) focusMode = !focusMode; true }
                    else -> false
                }
            },
    ) {
        val w = maxWidth; val h = maxHeight
        slots.forEachIndexed { i, s ->
            val isActive = i == active
            // Partage : deux écrans 16:9 côte à côte. Focus : l'actif à 76 %, l'autre en vignette en haut à droite.
            val targetW = when { !focusMode -> w / 2 - 12.dp; isActive -> w * 0.76f; else -> w * 0.22f }
            val targetX = when { !focusMode -> if (i == 0) 8.dp else w / 2 + 4.dp; isActive -> 24.dp; else -> w - w * 0.22f - 24.dp }
            val targetY = when { !focusMode -> (h - (w / 2 - 12.dp) * 9f / 16f) / 2; isActive -> (h - w * 0.76f * 9f / 16f) / 2; else -> 32.dp }
            val ww by animateFloatAsState(targetW.value, spring(stiffness = 300f), label = "w$i")
            val xx by animateFloatAsState(targetX.value, spring(stiffness = 300f), label = "x$i")
            val yy by animateFloatAsState(targetY.value, spring(stiffness = 300f), label = "y$i")
            Box(
                Modifier.offset(xx.dp, yy.dp).size(ww.dp, (ww * 9f / 16f).dp).clip(RoundedCornerShape(10.dp))
                    .border(if (isActive) 3.dp else 1.dp, if (isActive) Aditv.Accent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .background(Aditv.Surface1),
            ) {
                s.player?.let { p ->
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; keepScreenOn = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player = p } },
                        update = { it.player = p }, modifier = Modifier.fillMaxSize())
                }
                s.error?.let { e -> Text(e, style = MaterialTheme.typography.bodyMedium, color = Aditv.Warn, modifier = Modifier.align(Alignment.Center).padding(16.dp)) }
                if (hint || !isActive) Column(Modifier.align(Alignment.TopStart).padding(10.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text((if (isActive) "🔊 " else "") + s.title, style = MaterialTheme.typography.labelLarge, color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (hint) Text("← → : écran actif (le son suit)  ·  OK : agrandir / partager  ·  Retour : plein écran",
            style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}
