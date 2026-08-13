package com.sloflix.tv.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.sloflix.tv.ui.TestTags
import com.sloflix.tv.ui.components.SloflixLogo
import com.sloflix.tv.ui.components.forceShowSoftKeyboard
import com.sloflix.tv.ui.components.hideSoftKeyboard
import com.sloflix.tv.ui.i18n.LocalStrings

private val Background = Color(0xFF090C12)
private val Panel = Color(0xFF141923)
private val Accent = Color(0xFFE50913)
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
    val strings = LocalStrings.current
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
            SloflixLogo(markSize = 32.dp, textSize = 28.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings.signInToWatch,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Spacer(Modifier.height(32.dp))

            TvTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                label = strings.username,
                enabled = !state.isLoading,
                focusRequester = usernameFocus,
                previousFocus = FocusRequester.Default,
                nextFocus = passwordFocus,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                testTag = TestTags.LoginUsername,
            )
            Spacer(Modifier.height(16.dp))
            TvTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = strings.password,
                enabled = !state.isLoading,
                focusRequester = passwordFocus,
                previousFocus = usernameFocus,
                nextFocus = submitFocus,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                testTag = TestTags.LoginPassword,
            )

            if (state.errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorText,
                    modifier = Modifier.testTag(TestTags.LoginError),
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(submitFocus)
                    .focusProperties { up = passwordFocus }
                    .testTag(TestTags.LoginSubmit),
            ) {
                Text(if (state.isLoading) strings.signingIn else strings.signIn)
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
    testTag: String? = null,
) {
    val taggedModifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
    // Focus navigates the field shell; IME opens only after OK/click.
    var isEditing by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var textHadFocus by remember { mutableStateOf(false) }
    var restoreShellFocus by remember { mutableStateOf(false) }
    val textFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val borderColor = if (isFocused || isEditing) Accent else Color(0xFF596274)
    val displayValue = if (visualTransformation is PasswordVisualTransformation && value.isNotEmpty()) {
        "•".repeat(value.length)
    } else {
        value
    }

    fun finishEditing() {
        isEditing = false
        hideSoftKeyboard(view, keyboard)
        nextFocus.requestFocus()
    }

    fun handleBack(): Boolean {
        return when {
            isEditing -> {
                restoreShellFocus = true
                isEditing = false
                hideSoftKeyboard(view, keyboard)
                true
            }
            isFocused -> {
                focusManager.clearFocus()
                true
            }
            else -> false
        }
    }

    BackHandler(enabled = isEditing || isFocused) {
        handleBack()
    }

    val backKeyModifier = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            handleBack()
        } else {
            false
        }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            textHadFocus = false
            forceShowSoftKeyboard(view, textFocus, keyboard)
        } else {
            textHadFocus = false
            hideSoftKeyboard(view, keyboard)
            if (restoreShellFocus) {
                focusRequester.requestFocus()
                restoreShellFocus = false
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused || isEditing) Color.White else SecondaryText,
        )
        Spacer(modifier.height(8.dp))
        if (isEditing) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                cursorBrush = SolidColor(Accent),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(
                    onNext = { finishEditing() },
                    onDone = { finishEditing() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(taggedModifier)
                    .then(backKeyModifier)
                    .focusRequester(textFocus)
                    .focusProperties {
                        up = previousFocus
                        down = nextFocus
                    }
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            textHadFocus = true
                        } else if (textHadFocus) {
                            isEditing = false
                        }
                    }
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
        } else {
            val fieldShape = RoundedCornerShape(8.dp)
            val idleBorder = Border(BorderStroke(2.dp, Color(0xFF596274)), shape = fieldShape)
            val accentBorder = Border(BorderStroke(2.dp, Accent), shape = fieldShape)
            Surface(
                onClick = { if (enabled) isEditing = true },
                enabled = enabled,
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(
                    border = idleBorder,
                    focusedBorder = accentBorder,
                    pressedBorder = accentBorder,
                ),
                shape = ClickableSurfaceDefaults.shape(shape = fieldShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Background,
                    focusedContainerColor = Background,
                    pressedContainerColor = Background,
                    disabledContainerColor = Background,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(taggedModifier)
                    .then(backKeyModifier)
                    .focusRequester(focusRequester)
                    .focusProperties {
                        up = previousFocus
                        down = nextFocus
                    }
                    .onFocusChanged { isFocused = it.isFocused },
            ) {
                Text(
                    text = displayValue.ifEmpty { "Enter $label" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isEmpty()) Color(0xFF8E98AA) else Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}
