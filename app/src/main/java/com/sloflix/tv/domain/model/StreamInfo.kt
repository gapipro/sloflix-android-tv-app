package com.sloflix.tv.domain.model

/**
 * [url] is the preferred stream; [fallbackUrls] are the remaining `media_sources` in preference
 * order so the player can move on when a source turns out to be unplayable.
 * [subtitles] are WebVTT (or similar) tracks from the Sloflix player / API, matching the web player.
 */
data class StreamInfo(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val fallbackUrls: List<String> = emptyList(),
    val subtitles: List<SubtitleTrack> = emptyList(),
) {
    val candidateUrls: List<String>
        get() = listOf(url) + fallbackUrls
}

data class SubtitleTrack(
    val url: String,
    val language: String = "sl",
    val label: String = "Slovenski",
)
