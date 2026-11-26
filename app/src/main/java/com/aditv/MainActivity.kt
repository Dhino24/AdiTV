package com.aditv

import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.aditv.ui.LocalContainer
import com.aditv.ui.Root
import com.aditv.ui.theme.AditvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AditvApp).container
        handleReminderIntent(intent)
        // Télécommande sur le téléphone : touches rejouées dans l'appli (jamais dans les autres applis).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                com.aditv.ui.remote.PhoneRemote.commands.collect { cmd -> onPhoneCommand(cmd) }
            }
        }
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                AditvTheme { Root() }
            }
        }
    }

    /** Toute touche de la télécommande : ADITV est en cours d'utilisation (le guide TV attend). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        com.aditv.core.UserActivity.touch()
    }

    private fun onPhoneCommand(cmd: com.aditv.ui.remote.PhoneRemote.Command) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        fun key(code: Int) {
            window.decorView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            window.decorView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        when (cmd.key) {
            "up" -> key(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> key(KeyEvent.KEYCODE_DPAD_DOWN)
            "left" -> key(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> key(KeyEvent.KEYCODE_DPAD_RIGHT)
            "ok" -> key(KeyEvent.KEYCODE_DPAD_CENTER)
            "menu" -> key(KeyEvent.KEYCODE_MENU)
            "play" -> key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "rew" -> key(KeyEvent.KEYCODE_MEDIA_REWIND)
            "fwd" -> key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "chup" -> key(KeyEvent.KEYCODE_CHANNEL_UP)
            "chdown" -> key(KeyEvent.KEYCODE_CHANNEL_DOWN)
            "back" -> { onUserInteraction(); onBackPressedDispatcher.onBackPressed() }
            "volup" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "voldown" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "home" -> { onUserInteraction(); com.aditv.ui.remote.PhoneRemote.route.value = "home" }
            "live" -> { onUserInteraction(); com.aditv.ui.remote.PhoneRemote.route.value = "live" }
            "search" -> { onUserInteraction(); com.aditv.ui.remote.PhoneRemote.searchQuery.value = cmd.text; com.aditv.ui.remote.PhoneRemote.route.value = "search" }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleReminderIntent(intent)
    }

    /** Notification de rappel choisie : la chaîne du match s'ouvre dans le lecteur. */
    private fun handleReminderIntent(intent: android.content.Intent?) {
        intent?.getStringExtra(com.aditv.data.sport.ReminderScheduler.EXTRA_CHANNEL)?.let { com.aditv.data.sport.PendingLaunch.channel.value = it }
    }
}
