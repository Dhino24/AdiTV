package com.aditv.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import com.aditv.data.profile.Profile
import com.aditv.ui.common.navSound
import com.aditv.ui.design.premiumBackground
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit

/** Couleurs d'avatar (charte : sobres, une par profil). */
private val avatarColors = listOf(
    Color(0xFF22D3C5), Color(0xFFFF6B57), Color(0xFFE0B75F), Color(0xFF7EE7F2), Color(0xFF26C281), Color(0xFFB39DDB), Color(0xFFF48FB1),
)
fun avatarColor(p: Profile) = avatarColors[p.color.mod(avatarColors.size)]

/** Pastille ronde avec l'initiale du profil. */
@Composable
fun Avatar(p: Profile, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(avatarColor(p).copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.86f).background(avatarColor(p), CircleShape), contentAlignment = Alignment.Center) {
            Text(p.name.take(1).uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp, color = Aditv.OnLight)
        }
    }
}

/** « Qui regarde ? » au démarrage, quand la maison a plusieurs profils. */
@Composable
fun ProfilePicker(profiles: List<Profile>, activeId: String, onPick: (Profile) -> Unit) {
    val first = remember { FocusRequester() }
    com.aditv.ui.common.InitialFocus(first)
    Box(Modifier.fillMaxSize().premiumBackground(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Qui regarde ?", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = Aditv.Text)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                profiles.forEach { p ->
                    var focused by remember { mutableStateOf(false) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onPick(p) },
                            modifier = (if (p.id == activeId) Modifier.focusRequester(first) else Modifier).size(96.dp).onFocusChanged { focused = it.isFocused }.navSound(),
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Aditv.Text), shape = CircleShape, inset = 3.dp)),
                        ) { Avatar(p, 96.dp) }
                        Spacer(Modifier.height(12.dp))
                        Text(p.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = if (focused) Aditv.Text else Aditv.TextSecondary)
                    }
                }
            }
        }
    }
}

/** Le choix du profil n'est demandé qu'une fois par lancement. */
object ProfileGate { var chosen = false }
