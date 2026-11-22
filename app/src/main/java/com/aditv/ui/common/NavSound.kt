package com.aditv.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.aditv.R
import com.aditv.core.UserActivity

/**
 * Petit son doux de navigation (façon Apple TV) : joué quand la sélection se déplace sous l'action de la télécommande,
 * jamais au chargement d'un écran ni en butée. Sons courts et originaux (res/raw/nav_*.wav), volume bas,
 * limités en cadence quand une touche est maintenue. Désactivable dans Réglages.
 */
object NavSound {
    @Volatile var enabled = true
    private var pool: SoundPool? = null
    private var move = 0
    private var select = 0
    @Volatile private var lastPlay = 0L

    fun init(context: Context) {
        if (pool != null) return
        runCatching {
            val p = SoundPool.Builder().setMaxStreams(2)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build()
            move = p.load(context, R.raw.nav_move, 1)
            select = p.load(context, R.raw.nav_select, 1)
            pool = p
        }
    }

    /** Sélection arrivée sur un élément : son seulement si une touche vient d'être pressée (pas de son au chargement). */
    fun focused() {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - UserActivity.lastInputAt > 350 || now - lastPlay < 55) return
        lastPlay = now
        runCatching { pool?.play(move, 0.35f, 0.35f, 0, 0, 1f) }
    }

    /** Validation (OK). */
    fun selected() {
        if (!enabled) return
        lastPlay = System.currentTimeMillis()
        runCatching { pool?.play(select, 0.4f, 0.4f, 0, 0, 1f) }
    }
}

/** À poser sur chaque élément sélectionnable à la télécommande. */
fun Modifier.navSound(): Modifier = onFocusChanged { if (it.isFocused) NavSound.focused() }
