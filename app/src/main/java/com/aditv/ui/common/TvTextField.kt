package com.aditv.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.aditv.ui.theme.Aditv

/**
 * Champ texte adapté à la télécommande : le D-pad traverse le champ sans ouvrir le clavier ;
 * OK active la saisie (clavier système), Retour ou « Terminé » la referme.
 */
@Composable
fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    revealed: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    surfaceRequester: FocusRequester = remember { FocusRequester() },
    modifierEntry: FocusRequester? = null,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val fieldRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)

    LaunchedEffect(editing) {
        if (editing) { fieldRequester.requestFocus(); keyboard?.show() }
    }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Aditv.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Surface(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth().height(52.dp).focusRequester(surfaceRequester).then(if (modifierEntry != null) Modifier.focusRequester(modifierEntry) else Modifier),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Aditv.Surface1, focusedContainerColor = Aditv.Surface2,
                contentColor = Aditv.Text, focusedContentColor = Aditv.Text,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Aditv.Line), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, Aditv.Accent), shape = shape),
            ),
        ) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && !editing) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary.copy(alpha = 0.7f))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Aditv.Text),
                    cursorBrush = SolidColor(Aditv.Accent),
                    visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (password) KeyboardType.Password else keyboardType,
                        imeAction = imeAction, autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onAny = {
                        keyboard?.hide(); editing = false; surfaceRequester.requestFocus(); onImeAction()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldRequester)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing) editing = false },
                )
            }
        }
    }
}
