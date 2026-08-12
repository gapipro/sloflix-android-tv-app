package com.sloflix.tv.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private val Background = Color(0xFF090C12)
private val Panel = Color(0xFF141923)
private val Accent = Color(0xFFE52B3D)
private val SecondaryText = Color(0xFFBFC7D5)
private val ErrorText = Color(0xFFFFA9B1)

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        usernameFocus.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(Panel, RoundedCornerShape(16.dp))
                .padding(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "SLOFLIX",
                color = Accent,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Sign in to watch",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Use your Sloflix account to continue.",
                style = MaterialTheme.typography.bodyLarge,
                color = SecondaryText,
            )
            Spacer(Modifier.height(32.dp))

            TvTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                label = "Username",
                enabled = !state.isLoading,
                focusRequester = usernameFocus,
                previousFocus = FocusRequester.Default,
                nextFocus = passwordFocus,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            Spacer(Modifier.height(16.dp))
            TvTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = "Password",
                enabled = !state.isLoading,
                focusRequester = passwordFocus,
                previousFocus = usernameFocus,
                nextFocus = submitFocus,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitFocus.requestFocus() }),
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorText,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(submitFocus)
                    .focusProperties { up = passwordFocus },
            ) {
                Text(if (state.isLoading) "Signing in…" else "Sign in")
            }
        }
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    previousFocus: FocusRequester,
    nextFocus: FocusRequester,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isFocused -> Accent
        else -> Color(0xFF596274)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) Color.White else SecondaryText,
        )
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            cursorBrush = SolidColor(Accent),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusProperties {
                    up = previousFocus
                    down = nextFocus
                }
                .onFocusChanged { isFocused = it.isFocused }
                .background(Background, RoundedCornerShape(8.dp))
                .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Enter $label",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF8E98AA),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
