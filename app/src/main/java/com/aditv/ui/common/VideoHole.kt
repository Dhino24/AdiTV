package com.aditv.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Vidéo YouTube dans le WebView de la Fire TV : l'image est affichée DERRIÈRE la fenêtre (« trou » dans l'interface),
 * le son passe quoi qu'il arrive. Tant qu'une bande-annonce est à l'écran, tout ce qui est derrière elle doit être
 * transparent (fenêtre, fonds d'écran, visuel de la vedette), sinon on entend le son sans voir l'image.
 */
object VideoHole {
    /** Nombre de lecteurs de bande-annonce présents (plein écran ou fond). */
    var embeds by mutableIntStateOf(0)
    /** Une bande-annonce de fond joue réellement : le visuel derrière elle n'est plus dessiné. */
    var backgroundPlaying by mutableStateOf(false)
    val active get() = embeds > 0
}
