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

private enum class Panel { NONE, AUDIO, VERSION, ENGINE, SLEEP }

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(session: Session.Active, contentId: String, fromStart: Boolean, liveList: String? = null, onExit: () -> Unit) {
    val c = LocalContainer.current
    val vm: PlayerViewModel = viewModel(key = "player-$contentId") { PlayerViewModel(c, session, contentId, fromStart, liveList) }
    val engine = vm.engine
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
    // Minuterie de veille : arrêt de la lecture et sortie du lecteur (le téléviseur se met ensuite en veille selon ses réglages).
    var sleepAt by remember { mutableStateOf<Long?>(null) }
    var sleepLeft by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(sleepAt) {
        while (sleepAt != null) {
            val left = sleepAt!! - System.currentTimeMillis()
            sleepLeft = left
            if (left <= 0) { sleepAt = null; vm.close(); onExit(); break }
            delay(1_000)
        }
        if (sleepAt == null) sleepLeft = null
    }
    LaunchedEffect(osd, position) { if (osd != 0) { delay(900); osd = 0 } }
    val clock by produceState(java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date())) {
        while (true) { delay(15_000); value = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date()) }
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
    LaunchedEffect(engine) {
        while (engine != null) {
            position = engine.positionMs; buffered = engine.bufferedMs
            duration = engine.durationMs
            playing = engine.isPlaying; buffering = engine.isBuffering
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
        val p = engine ?: return
        if (vm.isLive || !p.seekable) return
        p.seekTo((p.positionMs + ms).coerceIn(0, (p.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)))
        position = p.positionMs
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Dernières 60 s de la minuterie : n'importe quelle touche prolonge de 30 min.
                if ((sleepLeft ?: Long.MAX_VALUE) < 60_000) { sleepAt = System.currentTimeMillis() + 30 * 60_000L; return@onPreviewKeyEvent true }
                val p = engine
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
        // Surface vidéo du moteur actif (Media3 ou VLC), recréée au changement de moteur.
        engine?.let { e -> androidx.compose.runtime.key(e) { e.VideoSurface(Modifier.fillMaxSize()) } }

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

        sleepLeft?.takeIf { it < 60_000 }?.let { left ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 30.dp).background(Color(0xEB10151C), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_power), contentDescription = null, tint = Aditv.Accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(9.dp))
                Text("Minuterie : arrêt dans ${left / 1000} s · une touche pour prolonger de 30 min", style = MaterialTheme.typography.bodyLarge, color = Aditv.Text)
            }
        }

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

        if (controlsVisible && vm.error == null && engine != null) {
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
                        { engine.let { if (it.isPlaying) it.pause() else it.play() }; touch() }, Modifier.focusRequester(playFocus), size = 42.dp)
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
                    PillButton("Moteur : " + vm.engineChoice.label + if (vm.engineChoice == com.aditv.playback.engine.EngineChoice.AUTO) " · ${engine.kind.label}" else "", { panel = Panel.ENGINE }, icon = R.drawable.ic_tv, primary = false, small = true)
                    PillButton("Turbo", { vm.toggleTurbo(!vm.turbo); touch() }, icon = R.drawable.ic_bolt, primary = false, small = true, secondary = if (vm.turbo) "activé" else null)
                    PillButton("Statistiques", { statsOpen = !statsOpen; touch() }, icon = R.drawable.ic_chart, primary = false, small = true)
                    PillButton("Veille", { panel = Panel.SLEEP }, icon = R.drawable.ic_power, primary = false, small = true,
                        secondary = sleepLeft?.let { "${(it / 60_000) + 1} min" })
                }
                Text(
                    if (vm.isLive) "Commandes masquées : ↑/↓ chaîne précédente / suivante · OK afficher"
                    else "Commandes masquées : ←/→ −/+ 10 s · OK afficher",
                    style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary,
                )
            }
        }

        if (statsOpen && engine != null && vm.error == null) StatsPanel(engine, vm.turbo, Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 20.dp))

        if (panel == Panel.VERSION) {
            Box(Modifier.fillMaxSize().background(Color(0x80030508)))
            VersionPanel(vm.versions, onPick = { vm.switchVersion(it); panel = Panel.NONE; touch() }, modifier = Modifier.align(Alignment.CenterEnd))
        } else if (panel == Panel.SLEEP) {
            Box(Modifier.fillMaxSize().background(Color(0x80030508)))
            val liveEnd = vm.nowNext.firstOrNull { it.isLive(System.currentTimeMillis()) }?.endUtc
            SleepPanel(sleepAt != null, liveEnd, duration.takeIf { !vm.isLive && it > 0 }?.let { System.currentTimeMillis() + (it - position) }, onPick = { at ->
                sleepAt = at; panel = Panel.NONE; touch()
            }, modifier = Modifier.align(Alignment.CenterEnd))
        } else if (panel == Panel.ENGINE) {
            Box(Modifier.fillMaxSize().background(Color(0x80030508)))
            EnginePanel(vm.engineChoice, engine?.kind, vm.engineNote, onPick = { vm.chooseEngine(it); panel = Panel.NONE; touch() }, modifier = Modifier.align(Alignment.CenterEnd))
        } else if (panel != Panel.NONE && engine != null) {
            Box(Modifier.fillMaxSize().background(Color(0x80030508)))
            TrackPanel(engine, onClose = { panel = Panel.NONE; touch() }, modifier = Modifier.align(Alignment.CenterEnd))
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
@Composable
private fun TrackPanel(engine: com.aditv.playback.engine.PlaybackEngine, onClose: () -> Unit, modifier: Modifier) {
    val audio = remember(engine) { engine.audioTracks() }
    val text = remember(engine) { engine.textTracks() }
    val textDisabled = engine.textDisabled
    val first = remember { FocusRequester() }
    InitialFocus(first)
    SideSheet("Audio et sous-titres", "Pistes exposées par le flux, telles que le moteur ${engine.kind.label} les lit.", modifier) {
        LazyColumn {
            item { SheetHeader("Audio") }
            if (audio.isEmpty()) item { Text("Aucune piste audio exposée par le flux.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary) }
            items(audio, key = { "a${it.group}-${it.id}" }) { o ->
                SheetOption(o.label, { engine.selectAudio(o); onClose() }, if (o == audio.first()) Modifier.focusRequester(first) else Modifier, checked = o.selected)
            }
            item { SheetHeader("Sous-titres") }
            item {
                SheetOption("Désactivés", { engine.selectText(null); onClose() },
                    if (audio.isEmpty()) Modifier.focusRequester(first) else Modifier, checked = textDisabled || text.none { it.selected })
            }
            if (text.isEmpty()) item { Text("Aucun sous-titre dans ce flux.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary, modifier = Modifier.padding(start = 11.dp, top = 4.dp)) }
            items(text, key = { "t${it.group}-${it.id}" }) { o -> SheetOption(o.label, { engine.selectText(o); onClose() }, checked = o.selected && !textDisabled) }
        }
    }
}

/** Choix du moteur pour ce contenu : mémorisé, appliqué tout de suite à la même position. */
@Composable
private fun EnginePanel(choice: com.aditv.playback.engine.EngineChoice, current: com.aditv.playback.engine.EngineKind?, note: String?, onPick: (com.aditv.playback.engine.EngineChoice) -> Unit, modifier: Modifier) {
    val first = remember { FocusRequester() }
    InitialFocus(first)
    SideSheet("Moteur de lecture", "Mémorisé pour ce contenu. En cours : ${current?.label ?: "—"}." + (note?.let { " $it" } ?: ""), modifier) {
        LazyColumn {
            item { SheetHeader("Pour ce contenu") }
            items(com.aditv.playback.engine.EngineChoice.entries.toList(), key = { it.name }) { c ->
                SheetOption(c.label, { onPick(c) }, if (c == com.aditv.playback.engine.EngineChoice.AUTO) Modifier.focusRequester(first) else Modifier, checked = c == choice)
                Text(c.detail, style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, modifier = Modifier.padding(start = 29.dp, bottom = 6.dp))
            }
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

private fun natureColors(n: com.aditv.playback.engine.Nature): Pair<Color, Color> = when (n) {
    com.aditv.playback.engine.Nature.MEASURED -> Color(0xFF7FF0F0) to Color(0x2400D7D7)
    com.aditv.playback.engine.Nature.ESTIMATED -> Color(0xFFF6D58A) to Color(0x1FF6D58A)
    com.aditv.playback.engine.Nature.DECLARED -> Color(0xFFC7CFDB) to Color(0x1AC7CFDB)
    com.aditv.playback.engine.Nature.NA -> Color(0xFF7D8896) to Color(0x0DFFFFFF)
}

/**
 * Statistiques lues dans le moteur actif chaque seconde. Chaque valeur porte sa nature :
 * mesurée, estimée, déclarée (annoncée par le flux) ou non disponible. Pas de recopie d'un moteur à l'autre.
 */
@Composable
private fun StatsPanel(engine: com.aditv.playback.engine.PlaybackEngine, turbo: Boolean, modifier: Modifier) {
    val tick by produceState(0) { while (true) { delay(1_000); value++ } }
    val rows = remember(engine, tick) { engine.stats() }
    Column(modifier.width(290.dp).background(Color(0xD60C1016), RoundedCornerShape(11.dp)).padding(horizontal = 15.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Statistiques · ${engine.kind.label}", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 13.sp), color = Aditv.Text, modifier = Modifier.weight(1f))
            Text(if (turbo) "Turbo · chaque seconde" else "chaque seconde", style = MaterialTheme.typography.bodySmall.copy(fontSize = 7.5.sp), color = if (turbo) Aditv.Accent else Aditv.TextTertiary)
        }
        Spacer(Modifier.height(6.dp))
        rows.forEach { r ->
            val (fg, bg) = natureColors(r.nature)
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.07f)))
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 8.5.sp), color = Aditv.TextSecondary, modifier = Modifier.weight(1f))
                Text(r.value, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Aditv.Text, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Box(Modifier.width(42.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(r.nature.label.uppercase(), fontSize = 5.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = fg, modifier = Modifier.background(bg, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("« Déclaré » = annoncé par le flux, non vérifié. Les mesures de Media3 et de VLC ne sont pas comparables terme à terme.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 7.sp, lineHeight = 10.sp), color = Aditv.TextTertiary)
    }
}

/** Minuterie de veille : arrêt de la lecture après un délai, à la fin du programme ou du film. */
@Composable
private fun SleepPanel(active: Boolean, programmeEnd: Long?, contentEnd: Long?, onPick: (Long?) -> Unit, modifier: Modifier) {
    val first = remember { FocusRequester() }
    InitialFocus(first)
    val now = System.currentTimeMillis()
    val fmt = remember { java.text.SimpleDateFormat("HH:mm", Locale.FRANCE) }
    SideSheet("Minuterie de veille", "La lecture s'arrête et le lecteur se ferme ; le téléviseur se met ensuite en veille selon ses propres réglages.", modifier) {
        LazyColumn {
            item { SheetOption("Désactivée", { onPick(null) }, Modifier.focusRequester(first), checked = !active) }
            items(listOf(15, 30, 60, 90, 120)) { m -> SheetOption("Dans $m min", { onPick(now + m * 60_000L) }, detail = fmt.format(java.util.Date(now + m * 60_000L))) }
            if (programmeEnd != null) item { SheetOption("Fin du programme", { onPick(programmeEnd) }, detail = fmt.format(java.util.Date(programmeEnd))) }
            if (contentEnd != null) item { SheetOption("Fin de la lecture", { onPick(contentEnd) }, detail = fmt.format(java.util.Date(contentEnd))) }
        }
    }
}
