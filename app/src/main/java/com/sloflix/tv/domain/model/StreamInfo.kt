package com.sloflix.tv.domain.model

/**
 * [url] is the preferred stream; [fallbackUrls] are the remaining `media_sources` in preference
 * order so the player can move on when a source turns out to be unplayable.
 */
data class StreamInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val fallbackUrls: List<String> = emptyList(),
) {
    val candidateUrls: List<String>
        get() = listOf(url) + fallbackUrls
}
