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
        setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                AditvTheme { Root() }
            }
        }
    }
}
