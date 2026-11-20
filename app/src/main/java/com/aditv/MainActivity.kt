package com.aditv

import android.os.Bundle
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
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                AditvTheme { Root() }
            }
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
