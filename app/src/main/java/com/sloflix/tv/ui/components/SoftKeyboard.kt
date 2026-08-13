package com.sloflix.tv.ui.components

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay

/**
 * Focuses [textFocus], then forces the system IME via Compose + InputMethodManager.
 * Waits a frame so BasicTextField's Editable is attached before showSoftInput.
 */
suspend fun forceShowSoftKeyboard(
    view: View,
    textFocus: FocusRequester,
    keyboard: SoftwareKeyboardController?,
) {
    // Wait until BasicTextField (swapped in with isEditing) is composed and attached.
    awaitFrame()
    awaitFrame()
    runCatching { textFocus.requestFocus() }
    keyboard?.show()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    view.requestFocus()
    val shown = imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    if (!shown) {
        // Some Android TV builds ignore SHOW_IMPLICIT for leanback.
        @Suppress("DEPRECATION")
        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        delay(50)
        keyboard?.show()
    }
}

fun hideSoftKeyboard(view: View, keyboard: SoftwareKeyboardController?) {
    keyboard?.hide()
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}
