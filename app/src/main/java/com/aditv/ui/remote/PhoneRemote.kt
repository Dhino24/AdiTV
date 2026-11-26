package com.aditv.ui.remote

import com.aditv.net.PhoneRemoteServer
import com.aditv.net.RemoteEntryServer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Télécommande sur le téléphone : un seul serveur pour l'appli, actif tant qu'on l'utilise (arrêt après 3 h sans
 * commande, ou depuis les Réglages). Les touches sont rejouées dans ADI TV par [com.aditv.MainActivity].
 */
object PhoneRemote {
    data class Ready(val url: String, val shortUrl: String, val pin: String)
    data class Command(val key: String, val text: String?)

    private val _ready = MutableStateFlow<Ready?>(null)
    val ready: StateFlow<Ready?> = _ready.asStateFlow()
    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()
    /** Recherche envoyée par le téléphone, consommée par l'écran Rechercher. */
    val searchQuery = MutableStateFlow<String?>(null)
    /** Écran demandé par le téléphone (home, live, search), consommé par la coque. */
    val route = MutableStateFlow<String?>(null)

    @Volatile private var server: PhoneRemoteServer? = null

    /** Démarre (ou réutilise) le serveur. À appeler hors du thread principal. */
    @Synchronized fun start(): Ready? {
        server?.takeIf { !it.closed }?.let { return _ready.value }
        val address = RemoteEntryServer.addressProvider() ?: run { _note.value = "La TV n'est pas sur un réseau local (Wi-Fi ou Ethernet)."; return null }
        val s = PhoneRemoteServer(address, onCommand = { k, t -> _commands.tryEmit(Command(k, t)) }, onClosed = { r -> _ready.value = null; _note.value = r })
        if (!s.start()) { _note.value = "Impossible d'ouvrir la télécommande sur le réseau local."; return null }
        server = s
        _note.value = null
        return Ready(s.url!!, s.shortUrl!!, s.pin).also { _ready.value = it }
    }

    @Synchronized fun stop() { server?.close("Télécommande arrêtée."); server = null; _ready.value = null }
}
