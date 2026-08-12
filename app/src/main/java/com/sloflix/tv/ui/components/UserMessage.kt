package com.sloflix.tv.ui.components

import java.io.IOException

private const val OfflineMessage = "You’re offline. Check your connection and try again."

fun Throwable.toUserMessage(fallback: String): String =
    if (this is IOException) OfflineMessage else message ?: fallback
