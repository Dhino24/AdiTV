package com.aditv.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.data.Session
import com.aditv.playback.TrackOption
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.AditvButton
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.theme.Aditv
import java.util.Locale
import kotlinx.coroutines.delay

private enum class Panel { NONE, AUDIO, TEXT }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(session: Session.Active, contentId: String, fromStart: Boolean, onExit: () -> Unit) {
    val c = LocalContainer.current
    val vm: PlayerViewModel = viewModel(key = "player-$contentId") { PlayerViewModel(c, session, contentId, fromStart) }
    val player = vm.player
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { vm.start() }
    fun touch() { lastInteraction = System.currentTimeMillis(); controlsVisible = true }
    fun exit() { vm.close(); onExit() }

    // Mise en veille / changement d'application : sauvegarde + libération, réouverture en pause.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> vm.suspendSession()
                Lifecycle.Event.ON_START -> vm.resumeSession()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // État du lecteur, rafraîchi 2×/s au plus.
    LaunchedEffect(player) {
        while (player != null) {
            position = player.currentPosition; buffered = player.bufferedPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            playing = player.isPlaying; buffering = player.playbackState == Player.STATE_BUFFERING
            delay(500)
        }
    }
    // Masquage automatique des commandes après 5 s de lecture sans action.
    LaunchedEffect(controlsVisible, lastInteraction, playing) {
        if (controlsVisible && playing && panel == Panel.NONE && vm.error == null) {
            delay(5_000); controlsVisible = false; runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(controlsVisible) { if (controlsVisible && vm.error == null) { delay(50); runCatching { playFocus.requestFocus() } } }
    InitialFocus(rootFocus)

    BackHandler {
        when {
            panel != Panel.NONE -> panel = Panel.NONE
            vm.nextUp != null -> vm.cancelNext()
            controlsVisible && vm.error == null && playing -> { controlsVisible = false; runCatching { rootFocus.requestFocus() } }
            else -> exit()
        }
    }

    fun seekBy(ms: Long) {
        val p = player ?: return
        if (vm.isLive || !p.isCurrentMediaItemSeekable) return
        p.seekTo((p.currentPosition + ms).coerceIn(0, (p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)))
        position = p.currentPosition
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val p = player
                when (ev.key) {
                    Key.MediaPlayPause -> { p?.let { if (it.isPlaying) it.pause() else it.play() }; touch(); true }
                    Key.MediaPlay -> { p?.play(); touch(); true }
                    Key.MediaPause -> { p?.pause(); touch(); true }
                    Key.MediaFastForward -> { seekBy(30_000); touch(); true }
                    Key.MediaRewind -> { seekBy(-30_000); touch(); true }
                    else -> {
                        if (!controlsVisible && panel == Panel.NONE) {
                            when (ev.key) {
                                // En VOD, ←/→ déplacent la lecture ; en direct, aucun seek inexistant.
                                Key.DirectionLeft -> { seekBy(-10_000); touch(); true }
                                Key.DirectionRight -> { seekBy(10_000); touch(); true }
                                Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown -> { touch(); true }
                                else -> false
                            }
                        } else { lastInteraction = System.currentTimeMillis(); false }
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (buffering && vm.error == null) {
            Text("Chargement…", style = MaterialTheme.typography.titleLarge, color = Aditv.Text, modifier = Modifier.align(Alignment.Center))
        }

        if (controlsVisible || vm.error != null) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent))).padding(horizontal = 40.dp, vertical = 28.dp)) {
                Text(vm.title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.isLive) {
                        Text("● DIRECT", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF5A6A))
                        Spacer(Modifier.width(10.dp))
                    } else vm.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary); Spacer(Modifier.width(10.dp)) }
                    Text("Moteur : Media3", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
                }
            }
        }

        if (controlsVisible && vm.error == null && player != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 40.dp, vertical = 28.dp),
            ) {
                if (!vm.isLive && duration > 0) {
                    SeekBar(position, buffered, duration)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(position), style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
                        Text("− " + fmt((duration - position).coerceAtLeast(0)), style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AditvButton(if (playing) "Pause" else "Lecture", { player.let { if (it.isPlaying) it.pause() else it.play() }; touch() }, Modifier.focusRequester(playFocus))
                    if (!vm.isLive) {
                        AditvButton("− 10 s", { seekBy(-10_000); touch() })
                        AditvButton("+ 10 s", { seekBy(10_000); touch() })
                    }
                    Spacer(Modifier.weight(1f))
                    AditvButton("Audio", { panel = Panel.AUDIO })
                    AditvButton("Sous-titres", { panel = Panel.TEXT })
                }
                if (!vm.isLive) Text(
                    "Commandes masquées : ←/→ = −/+ 10 s · OK = afficher",
                    style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        if (panel != Panel.NONE && player != null) {
            TrackPanel(player, panel == Panel.AUDIO, onClose = { panel = Panel.NONE; touch() }, modifier = Modifier.align(Alignment.CenterEnd))
        }

        vm.error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 640.dp).background(Aditv.Surface1, RoundedCornerShape(16.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Lecture impossible", style = MaterialTheme.typography.headlineSmall, color = Aditv.Text)
                Text(msg, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
                val first = remember { FocusRequester() }
                InitialFocus(first, key = msg)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.canRetry) AditvButton("Réessayer", { vm.retry() }, Modifier.focusRequester(first))
                    AditvButton("Retour", { exit() }, if (vm.canRetry) Modifier else Modifier.focusRequester(first))
                }
            }
        }

        vm.nextUp?.let { n ->
            Column(
                Modifier.align(Alignment.BottomEnd).padding(40.dp).widthIn(max = 520.dp).background(Aditv.Surface1, RoundedCornerShape(16.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Épisode suivant dans ${n.secondsLeft} s", style = MaterialTheme.typography.labelLarge, color = Aditv.Accent)
                Text("S${n.episode.season} É${n.episode.number ?: "?"} · ${n.episode.title}", style = MaterialTheme.typography.titleLarge, color = Aditv.Text)
                val f = remember { FocusRequester() }
                InitialFocus(f, key = n.episode.id)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AditvButton("Lire maintenant", { vm.playNext() }, Modifier.focusRequester(f))
                    AditvButton("Annuler", { vm.cancelNext(); controlsVisible = true })
                }
            }
        }
    }
}

@Composable
private fun SeekBar(position: Long, buffered: Long, duration: Long) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((buffered.toFloat() / duration).coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp)))
        Box(Modifier.fillMaxHeight().fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f)).background(Aditv.Accent, RoundedCornerShape(3.dp)))
    }
}

/** Pistes réellement présentes dans le flux, telles que le lecteur les expose. */
@OptIn(UnstableApi::class)
@Composable
private fun TrackPanel(player: Player, audio: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val type = if (audio) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
    val options = remember(player.currentTracks, audio) { trackOptions(player, type) }
    val textDisabled = !audio && player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val first = remember { FocusRequester() }
    InitialFocus(first)
    Column(modifier.fillMaxHeight().width(420.dp).background(Aditv.Surface1.copy(alpha = 0.96f)).padding(24.dp)) {
        Text(if (audio) "Audio" else "Sous-titres", style = MaterialTheme.typography.headlineSmall, color = Aditv.Text)
        Spacer(Modifier.height(12.dp))
        if (options.isEmpty()) Text(if (audio) "Aucune piste audio exposée par le flux." else "Aucun sous-titre dans ce flux.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!audio) item {
                TrackRow("Désactivés", textDisabled || options.none { it.selected }, Modifier.focusRequester(first)) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); onClose()
                }
            }
            items(options, key = { "${it.groupIndex}-${it.trackIndex}" }) { o ->
                val isFirst = audio && o == options.first()
                TrackRow(o.label, o.selected && !textDisabled, if (isFirst) Modifier.focusRequester(first) else Modifier) {
                    val group = player.currentTracks.groups[o.groupIndex].mediaTrackGroup
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group, o.trackIndex)).build()
                    onClose()
                }
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) = ListItem(
    selected = selected, onClick = onClick, modifier = modifier,
    headlineContent = { Text(label) },
    trailingContent = { if (selected) Text("✓") },
    colors = ListItemDefaults.colors(focusedContainerColor = Aditv.Text, focusedContentColor = Aditv.OnLight, selectedContainerColor = Aditv.Surface2),
)

@OptIn(UnstableApi::class)
private fun trackOptions(player: Player, type: Int): List<TrackOption> = buildList {
    player.currentTracks.groups.forEachIndexed { gi, g ->
        if (g.type != type) return@forEachIndexed
        for (ti in 0 until g.length) {
            if (!g.isTrackSupported(ti)) continue
            val f = g.getTrackFormat(ti)
            val lang = f.language?.let { runCatching { Locale.forLanguageTag(it).getDisplayLanguage(Locale.FRENCH) }.getOrNull() }?.ifBlank { null }
            val parts = listOfNotNull(
                f.label ?: lang?.replaceFirstChar { it.uppercase() } ?: "Piste ${size + 1}",
                lang?.takeIf { f.label != null }?.replaceFirstChar { it.uppercase() },
                f.channelCount.takeIf { it > 0 && type == C.TRACK_TYPE_AUDIO }?.let { if (it >= 6) "5.1" else if (it == 2) "stéréo" else "$it can." },
                f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { type == C.TRACK_TYPE_AUDIO },
            )
            add(TrackOption(gi, ti, parts.joinToString(" · "), g.isTrackSelected(ti)))
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
