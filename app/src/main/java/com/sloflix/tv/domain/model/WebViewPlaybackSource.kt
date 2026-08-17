package com.sloflix.tv.domain.model

/**
 * StreamP2P (and similar) HTML embeds. Resolved to HLS via decrypt for ExoPlayer;
 * WebView remains available as a fallback path.
 */
data class WebViewPlaybackSource(
    val label: String,
    val url: String,
)
