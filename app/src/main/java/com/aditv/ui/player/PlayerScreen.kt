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
import com.aditv.R
import com.aditv.ui.design.Kicker
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.RoundButton
import com.aditv.ui.design.SheetHeader
import com.aditv.ui.design.SheetOption
import com.aditv.ui.design.SideSheet
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.theme.Aditv
import java.util.Locale
import kotlinx.coroutines.delay

private enum class Panel { NONE, AUDIO, VERSION }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(session: Session.Active, contentId: String, fromStart: Boolean, liveList: String? = null, onExit: () -> Unit) {
    val c = LocalContainer.current
    val vm: PlayerViewModel = viewModel(key = "player-$contentId") { PlayerViewModel(c, session, contentId, fromStart, liveList) }
    val player = vm.player
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var bannerUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(bannerUntil) { if (bannerUntil > 0) { delay(4_000); bannerUntil = 0 } }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var scrubFocused by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var osd by remember { mutableIntStateOf(0) }
    var stalls by remember { mutableIntStateOf(0) }
    LaunchedEffect(osd, position) { if (osd != 0) { delay(900); osd = 0 } }
    val clock by produceState(java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date())) {
        while (true) { delay(15_000); value = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date()) }
    }
    // Interruptions mesurées : passage en chargement après un premier démarrage.
    DisposableEffect(player) {
        var started = false
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) started = true
                else if (state == Player.STATE_BUFFERING && started) stalls++
            }
        }
        player?.addListener(l)
        onDispose { player?.removeListener(l) }
    }
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
    LaunchedEffect(controlsVisible, panel) { if (controlsVisible && vm.error == null && panel == Panel.NONE) { delay(50); runCatching { playFocus.requestFocus() } } }
    InitialFocus(rootFocus)

    BackHandler {
        when {
            panel != Panel.NONE -> panel = Panel.NONE
            statsOpen && !controlsVisible -> statsOpen = false
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
                                Key.DirectionLeft -> { seekBy(-10_000); osd = -1; true }
                                Key.DirectionRight -> { seekBy(10_000); osd = 1; true }
                                // En direct, ↑/↓ changent de chaîne dans la liste d'origine (favoris, catégorie…).
                                Key.DirectionUp -> { if (vm.isLive) { vm.zap(-1); bannerUntil = System.currentTimeMillis() } else touch(); true }
                                Key.DirectionDown -> { if (vm.isLive) { vm.zap(1); bannerUntil = System.currentTimeMillis() } else touch(); true }
                                Key.ChannelUp -> { vm.zap(-1); bannerUntil = System.currentTimeMillis(); true }
                                Key.ChannelDown -> { vm.zap(1); bannerUntil = System.currentTimeMillis(); true }
                                Key.DirectionCenter, Key.Enter -> { touch(); true }
                                else -> false
                            }
                        } else {
                            lastInteraction = System.currentTimeMillis()
                            // Barre de lecture focalisée : ←/→ = −/+ 10 s.
                            if (scrubFocused && (ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight)) { seekBy(if (ev.key == Key.DirectionLeft) -10_000 else 10_000); true } else false
                        }
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

        if (buffering && vm.error == null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Spinner()
            Spacer(Modifier.height(12.dp))
            Text(if (vm.isLive) "Connexion à la chaîne…" else "Chargement…", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Aditv.TextSecondary)
        }

        // Saut ±10 s commandes masquées : retour visuel discret au centre.
        if (osd != 0 && !controlsVisible) Row(
            Modifier.align(Alignment.Center).background(Color(0xB30A0C10), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(if (osd < 0) R.drawable.ic_back10 else R.drawable.ic_fwd10), contentDescription = null, tint = Aditv.Text, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(fmt(position), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp), color = Aditv.Text)
        }

        if (!controlsVisible && vm.error == null && vm.isLive && bannerUntil > 0) ZapBanner(vm, Modifier.align(Alignment.BottomCenter))

        if (controlsVisible || vm.error != null) {
            Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
            Column(Modifier.padding(start = 48.dp, top = 36.dp).widthIn(max = 620.dp)) {
                if (vm.isLive) Row(verticalAlignment = Alignment.CenterVertically) {
                    LivePill()
                    vm.channelPosition?.let { Spacer(Modifier.width(8.dp)); Text("Chaîne $it", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary) }
                } else Kicker("En lecture")
                Spacer(Modifier.height(6.dp))
                Text(vm.title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!vm.isLive) vm.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, modifier = Modifier.padding(top = 4.dp)) }
                if (vm.isLive) NowNext(vm.nowNext)
            }
            Row(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Moteur : Media3", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary)
                Spacer(Modifier.width(10.dp))
                Text(clock, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Aditv.TextSecondary)
            }
        }

        if (controlsVisible && vm.error == null && player != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.88f))))
                    .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
            ) {
                if (!vm.isLive && duration > 0) {
                    SeekBar(position, buffered, duration, Modifier.onFocusChanged { scrubFocused = it.isFocused })
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(position), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Aditv.TextSecondary)
                        Text("− " + fmt((duration - position).coerceAtLeast(0)), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Aditv.TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(64.dp)) {
                    RoundButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Pause" else "Lecture",
                        { player.let { if (it.isPlaying) it.pause() else it.play() }; touch() }, Modifier.focusRequester(playFocus), size = 42.dp)
                    if (!vm.isLive) {
                        RoundButton(R.drawable.ic_back10, "Reculer de 10 s", { seekBy(-10_000); touch() }, overlay = "10")
                        RoundButton(R.drawable.ic_fwd10, "Avancer de 10 s", { seekBy(10_000); touch() }, overlay = "10")
                    } else {
                        RoundButton(R.drawable.ic_up, "Chaîne précédente", { vm.zap(-1); touch() })
                        RoundButton(R.drawable.ic_down, "Chaîne suivante", { vm.zap(1); touch() })
                        RoundButton(if (vm.isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline, if (vm.isFavorite) "Chaîne favorite" else "Ajouter aux favoris", { vm.toggleFavorite(); touch() }, active = vm.isFavorite)
                    }
                    Spacer(Modifier.weight(1f))
                    PillButton("Audio et sous-titres", { panel = Panel.AUDIO }, icon = R.drawable.ic_subs, primary = false, small = true)
                    if (vm.versions.size > 1) PillButton(vm.versions.firstOrNull { it.selected }?.label ?: "Version", { panel = Panel.VERSION }, icon = R.drawable.ic_layers, primary = false, small = true)
                    PillButton("Statistiques", { statsOpen = !statsOpen; touch() }, icon = R.drawable.ic_chart, primary = false, small = true)
                }
                Text(
                    if (vm.isLive) "Commandes masquées : ↑/↓ chaîne précédente / suivante · OK afficher"
                    else "Commandes masquées : ←/→ −/+ 10 s · OK afficher",
                    style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary,
                )
            }
        }

        if (statsOpen && player != null && vm.error == null) StatsPanel(player, stalls, Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 20.dp))

        if (panel == Panel.VERSION) {
            Box(Modifier.fillMaxSize().background(Color(0x80030508)))
            VersionPanel(vm.versions, onPick = { vm.switchVersion(it); panel = Panel.NONE; touch() }, modifier = Modifier.align(Alignment.CenterEnd))
        } else if (panel != Panel.NONE && player != null) {
            Box(Modifier.fillMaxSize().background(Color(0x80030508)))
            TrackPanel(player, onClose = { panel = Panel.NONE; touch() }, modifier = Modifier.align(Alignment.CenterEnd))
        }

        vm.error?.let { msg ->
            Box(Modifier.fillMaxSize().background(Color(0x9E030508)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 450.dp).background(Color(0xEB10151C), RoundedCornerShape(14.dp)).padding(horizontal = 28.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(42.dp).background(Aditv.Live.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_warn), contentDescription = null, tint = Aditv.Error, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Lecture impossible", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
                    Spacer(Modifier.height(7.dp))
                    Text(msg, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    val first = remember { FocusRequester() }
                    InitialFocus(first, key = msg)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (vm.canRetry) PillButton("Réessayer", { vm.retry() }, Modifier.focusRequester(first), icon = R.drawable.ic_retry)
                        if (vm.versions.size > 1) PillButton("Changer de version", { panel = Panel.VERSION }, icon = R.drawable.ic_layers, primary = false)
                        PillButton("Retour", { exit() }, if (vm.canRetry) Modifier else Modifier.focusRequester(first), icon = null, primary = !vm.canRetry)
                    }
                }
            }
        }

        vm.nextUp?.let { n ->
            Column(
                Modifier.align(Alignment.BottomEnd).padding(32.dp).widthIn(max = 300.dp).background(Color(0xEB10151C), RoundedCornerShape(12.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Kicker("Épisode suivant dans ${n.secondsLeft} s")
                Text("S${n.episode.season} É${n.episode.number ?: "?"} · ${n.episode.title}", style = MaterialTheme.typography.headlineSmall, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val f = remember { FocusRequester() }
                InitialFocus(f, key = n.episode.id)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PillButton("Lire maintenant", { vm.playNext() }, Modifier.focusRequester(f))
                    PillButton("Annuler", { vm.cancelNext(); controlsVisible = true }, icon = null, primary = false)
                }
            }
        }
    }
}

/** Barre de lecture focalisable : au focus elle s'épaissit, affiche un curseur ; ←/→ déplacent la lecture de 10 s. */
@Composable
private fun SeekBar(position: Long, buffered: Long, duration: Long, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val f = (position.toFloat() / duration).coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().height(20.dp).onFocusChanged { focused = it.isFocused }.focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(if (focused) 5.dp else 3.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((buffered.toFloat() / duration).coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.34f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Aditv.Accent))
        }
        if (focused) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.offset(x = maxWidth * f - 6.dp).size(12.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun LivePill() {
    Row(Modifier.background(Aditv.Live, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).background(Color.White, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("EN DIRECT", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White)
    }
}

/** Indicateur de chargement (fixe si les animations sont réduites). */
@Composable
private fun Spinner() {
    val reduced = com.aditv.ui.design.LocalReducedMotion.current
    val angle = if (reduced) 0f else androidx.compose.animation.core.rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "a",
    ).value
    androidx.compose.foundation.Canvas(Modifier.size(32.dp).graphicsLayer { rotationZ = angle }) {
        val w = 2.dp.toPx()
        drawArc(Color.White.copy(alpha = 0.14f), 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawArc(Aditv.Accent, -90f, if (reduced) 180f else 90f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Bandeau de zapping (commandes masquées, ↑/↓ en direct). */
@Composable
private fun ZapBanner(vm: PlayerViewModel, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = 48.dp, vertical = 32.dp).fillMaxWidth().height(95.dp)
            .background(Color(0xC70C1016), RoundedCornerShape(11.dp)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LivePill()
                vm.channelPosition?.let { Spacer(Modifier.width(8.dp)); Text("Chaîne $it", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary) }
            }
            Spacer(Modifier.height(5.dp))
            Text(vm.title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            NowNext(vm.nowNext, compact = true)
        }
        Text("↑ ↓ changer de chaîne\nOK afficher les commandes", style = MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp), color = Aditv.TextTertiary, textAlign = TextAlign.End)
    }
}

/** Pistes audio et sous-titres réellement présentes dans le flux, dans un seul panneau. */
@OptIn(UnstableApi::class)
@Composable
private fun TrackPanel(player: Player, onClose: () -> Unit, modifier: Modifier) {
    val audio = remember(player.currentTracks) { trackOptions(player, C.TRACK_TYPE_AUDIO) }
    val text = remember(player.currentTracks) { trackOptions(player, C.TRACK_TYPE_TEXT) }
    val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val first = remember { FocusRequester() }
    InitialFocus(first)
    fun select(type: Int, o: TrackOption) {
        val group = player.currentTracks.groups[o.groupIndex].mediaTrackGroup
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group, o.trackIndex)).build()
        onClose()
    }
    SideSheet("Audio et sous-titres", "Pistes exposées par le flux, telles que le lecteur les lit.", modifier) {
        LazyColumn {
            item { SheetHeader("Audio") }
            if (audio.isEmpty()) item { Text("Aucune piste audio exposée par le flux.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary) }
            items(audio, key = { "a${it.groupIndex}-${it.trackIndex}" }) { o ->
                SheetOption(o.label, { select(C.TRACK_TYPE_AUDIO, o) }, if (o == audio.first()) Modifier.focusRequester(first) else Modifier, checked = o.selected)
            }
            item { SheetHeader("Sous-titres") }
            item {
                SheetOption("Désactivés", {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build(); onClose()
                }, if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier, checked = textDisabled || text.none { it.selected })
            }
            if (text.isEmpty()) item { Text("Aucun sous-titre dans ce flux.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary, modifier = Modifier.padding(start = 11.dp, top = 4.dp)) }
            items(text, key = { "t${it.groupIndex}-${it.trackIndex}" }) { o -> SheetOption(o.label, { select(C.TRACK_TYPE_TEXT, o) }, checked = o.selected && !textDisabled) }
        }
    }
}

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

@Composable
private fun NowNext(list: List<com.aditv.data.epg.Programme>, compact: Boolean = false) {
    if (list.isEmpty()) { Text("Guide indisponible pour cette chaîne", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary, modifier = Modifier.padding(top = 4.dp)); return }
    val now = System.currentTimeMillis()
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
    Column(Modifier.padding(top = if (compact) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        list.take(if (compact) 2 else 3).forEachIndexed { i, p ->
            val live = p.isLive(now)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (live) "MAINTENANT" else if (i == 0) "À ${fmt.format(java.util.Date(p.startUtc))}" else "ENSUITE · ${fmt.format(java.util.Date(p.startUtc))}",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = if (live) Aditv.Accent else Aditv.TextTertiary, modifier = Modifier.width(100.dp))
                Text(p.title, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (live) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.width(110.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(((now - p.startUtc).toFloat() / (p.endUtc - p.startUtc)).coerceIn(0f, 1f)).background(Aditv.Accent))
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionPanel(options: List<VersionOption>, onPick: (String) -> Unit, modifier: Modifier) {
    val first = remember { FocusRequester() }
    InitialFocus(first)
    SideSheet("Versions", "Langue et qualité annoncées par le fournisseur ; la meilleure selon vos préférences est en tête. La position est conservée.", modifier) {
        LazyColumn {
            item { SheetHeader("Changer en gardant la position") }
            items(options, key = { it.itemId }) { o ->
                SheetOption(o.label, { onPick(o.itemId) }, if (o == options.first()) Modifier.focusRequester(first) else Modifier, checked = o.selected, detail = if (o == options.first()) "préférée" else null)
            }
        }
    }
}

private enum class Nature(val label: String, val fg: Color, val bg: Color) {
    MEASURED("Mesuré", Color(0xFF7FF0F0), Color(0x2400D7D7)),
    ESTIMATED("Estimé", Color(0xFFF6D58A), Color(0x1FF6D58A)),
    DECLARED("Déclaré", Color(0xFFC7CFDB), Color(0x1AC7CFDB)),
    NA("N/D", Color(0xFF7D8896), Color(0x0DFFFFFF)),
}

/**
 * Statistiques de lecture lues dans Media3 chaque seconde. Chaque valeur porte sa nature :
 * mesurée (lecteur), estimée (débit réseau), déclarée (flux) ou non disponible.
 */
@OptIn(UnstableApi::class)
@Composable
private fun StatsPanel(player: androidx.media3.exoplayer.ExoPlayer, stalls: Int, modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tick by produceState(0) { while (true) { delay(1_000); value++ } }
    @Suppress("UNUSED_EXPRESSION") tick
    val vf = player.videoFormat
    val af = player.audioFormat
    val size = player.videoSize
    val counters = player.videoDecoderCounters?.also { it.ensureUpdated() }
    val bandwidth = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(ctx).bitrateEstimate
    val reserve = ((player.bufferedPosition - player.currentPosition).coerceAtLeast(0) / 1000)
    fun mbps(b: Int) = "%.1f".format(b / 1_000_000f).replace('.', ',') + " Mb/s"
    val hdr = when (vf?.colorInfo?.colorTransfer) { C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"; C.COLOR_TRANSFER_HLG -> "HLG"; null -> null; else -> "SDR" }
    val rows = listOf(
        Triple("Moteur", "Media3", Nature.DECLARED),
        Triple("Image décodée", if (size.width > 0) "${size.width} × ${size.height}" else "N/D", if (size.width > 0) Nature.MEASURED else Nature.NA),
        Triple("Codec vidéo", vf?.codecs ?: vf?.sampleMimeType?.substringAfter('/') ?: "N/D", if (vf != null) Nature.DECLARED else Nature.NA),
        Triple("Images / s", vf?.frameRate?.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "N/D", if ((vf?.frameRate ?: 0f) > 0) Nature.DECLARED else Nature.NA),
        Triple("HDR", hdr ?: "N/D", if (hdr != null) Nature.DECLARED else Nature.NA),
        Triple("Débit vidéo", vf?.bitrate?.takeIf { it > 0 }?.let(::mbps) ?: "N/D", if ((vf?.bitrate ?: 0) > 0) Nature.DECLARED else Nature.NA),
        Triple("Bande passante", if (bandwidth > 0) "≈ " + mbps(bandwidth.toInt().coerceAtMost(Int.MAX_VALUE)) else "N/D", if (bandwidth > 0) Nature.ESTIMATED else Nature.NA),
        Triple("Réserve", "$reserve s", Nature.MEASURED),
        Triple("Images perdues", counters?.droppedBufferCount?.toString() ?: "N/D", if (counters != null) Nature.MEASURED else Nature.NA),
        Triple("Interruptions", stalls.toString(), Nature.MEASURED),
        Triple("Audio", af?.let { listOfNotNull(it.sampleMimeType?.substringAfter('/')?.uppercase(), it.channelCount.takeIf { c -> c > 0 }?.let { c -> if (c >= 6) "5.1" else if (c == 2) "stéréo" else "$c can." }).joinToString(" · ") } ?: "N/D", if (af != null) Nature.DECLARED else Nature.NA),
        Triple("Sortie HDMI", "N/D", Nature.NA),
    )
    Column(modifier.width(290.dp).background(Color(0xD60C1016), RoundedCornerShape(11.dp)).padding(horizontal = 15.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Statistiques de lecture", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 13.sp), color = Aditv.Text, modifier = Modifier.weight(1f))
            Text("chaque seconde", style = MaterialTheme.typography.bodySmall.copy(fontSize = 7.5.sp), color = Aditv.TextTertiary)
        }
        Spacer(Modifier.height(6.dp))
        rows.forEach { (k, v, n) ->
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.07f)))
            Row(Modifier.padding(vertical = 4.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 8.5.sp), color = Aditv.TextSecondary, modifier = Modifier.weight(1f))
                Text(v, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Aditv.Text, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Box(Modifier.width(42.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(n.label.uppercase(), fontSize = 5.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = n.fg, modifier = Modifier.background(n.bg, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Valeurs lues dans le lecteur Media3 ; « déclaré » = annoncé par le flux, non vérifié.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 7.sp, lineHeight = 10.sp), color = Aditv.TextTertiary)
    }
}
