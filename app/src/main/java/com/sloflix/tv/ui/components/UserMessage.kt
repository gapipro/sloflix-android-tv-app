package com.sloflix.tv.ui.components

import com.sloflix.tv.data.api.CloudflareChallengeException
import java.io.IOException

private const val OfflineMessage = "You’re offline. Check your connection and try again."

fun Throwable.toUserMessage(fallback: String): String = when {
    this is CloudflareChallengeException -> message
    this is IOException -> OfflineMessage
    else -> message ?: fallback
}
