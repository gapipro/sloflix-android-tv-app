package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.MediaSourceDto
import com.sloflix.tv.domain.model.SubtitleTrack
import com.sloflix.tv.domain.model.WebViewPlaybackSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Mirrors `UrlDataExtactor` in https://player.sloflix.com/index.js:
 * the web Video.js player only reads the `source` query parameter from the
 * player page URL and plays that as `video/mp4`. Subtitles come from the
 * `subtitle` query param, or from API `subtitle_location` under
 * `https://www.sloflix.com/subtitles/`.
 *
 * HTML player / embed pages themselves are not ExoPlayer-compatible and must
 * never be offered as direct candidates. StreamP2P embeds are listed separately
 * and resolved via [com.sloflix.tv.data.playback.StreamP2PClient] to HLS.
 */
internal object StreamSourceResolver {
    private const val PlayerHost = "player.sloflix.com"
    private const val SubtitleBaseUrl = "https://www.sloflix.com/subtitles/"
    private const val DefaultStreamP2pLabel = "StreamP2P"
    private const val DefaultDoodStreamLabel = "DoodStream"

    /** Hosts that serve interactive HTML players, not progressive media. */
    private val HtmlPlayerHosts = setOf(
        PlayerHost,
        "sf.strp2p.com",
        "strp2p.com",
    )

    fun candidates(sources: List<MediaSourceDto>): List<String> =
        sources.mapNotNull { source ->
            source.url.trim().takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()
        }.mapNotNull { url ->
            if (url.host.equals(PlayerHost, ignoreCase = true)) {
                playableSource(url)
            } else if (isHtmlPlayerHost(url.host)) {
                null
            } else {
                url.toString()
            }
        }.distinct()

    /** Short provider name for ExoPlayer/Dood sources, e.g. `DoodStream`. */
    fun exoSourceLabel(sources: List<MediaSourceDto>): String? {
        val labeled = sources.firstOrNull { source ->
            candidates(listOf(source)).isNotEmpty()
        } ?: return null
        return shortSourceLabel(labeled.name, DefaultDoodStreamLabel)
    }

    /** Short provider name for buttons, e.g. `SLOSubs (DoodStream)` → `DoodStream`. */
    fun shortSourceLabel(raw: String?, fallback: String): String {
        val name = raw?.trim().orEmpty()
        when {
            name.contains("StreamP2P", ignoreCase = true) -> return DefaultStreamP2pLabel
            name.contains("DoodStream", ignoreCase = true) -> return DefaultDoodStreamLabel
        }
        val inner = Regex("""\(([^)]+)\)""").findAll(name).lastOrNull()?.groupValues?.getOrNull(1)?.trim()
        if (!inner.isNullOrEmpty()) return inner
        return name.ifBlank { fallback }
    }

    /**
     * StreamP2P embed URLs resolved to HLS via decrypt (not played as HTML).
     * Matched when the host contains `strp2p.com` / `playerp2p.com` or the
     * source name mentions StreamP2P.
     */
    fun webViewEmbeds(sources: List<MediaSourceDto>): List<WebViewPlaybackSource> =
        sources.mapNotNull { source ->
            val url = source.url.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (!isStreamP2pSource(source.name, url)) return@mapNotNull null
            WebViewPlaybackSource(
                label = shortSourceLabel(source.name, DefaultStreamP2pLabel),
                url = url,
            )
        }.distinctBy { it.url }

    /**
     * Prefer an explicit `subtitle=` URL on the player page (web player), then fall back to
     * `subtitle_location` files hosted by Sloflix.
     */
    fun subtitles(sources: List<MediaSourceDto>): List<SubtitleTrack> {
        val fromPlayerQuery = sources.mapNotNull { source ->
            source.url.trim().toHttpUrlOrNull()
                ?.takeIf { it.host.equals(PlayerHost, ignoreCase = true) }
                ?.let(::subtitleFromPlayerQuery)
        }
        val fromApi = sources.mapNotNull { source ->
            source.subtitleLocation
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { location ->
                    SubtitleTrack(
                        url = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            SubtitleBaseUrl + location.trimStart('/')
                        },
                    )
                }
        }
        return (fromPlayerQuery + fromApi).distinctBy { it.url }
    }

    private fun playableSource(playerUrl: HttpUrl): String? {
        val encoded = playerUrl.queryParameter("source") ?: return null
        val decoded = decodeQueryValue(encoded) ?: return null
        val upstream = decoded.toHttpUrlOrNull() ?: return null
        if (upstream.host.equals(PlayerHost, ignoreCase = true)) return null
        if (isHtmlPlayerHost(upstream.host)) return null
        return upstream.toString()
    }

    private fun subtitleFromPlayerQuery(playerUrl: HttpUrl): SubtitleTrack? {
        val encoded = playerUrl.queryParameter("subtitle") ?: return null
        val decoded = decodeQueryValue(encoded) ?: return null
        val url = decoded.toHttpUrlOrNull()?.toString() ?: return null
        return SubtitleTrack(url = url)
    }

    private fun decodeQueryValue(encoded: String): String? {
        val trimmed = encoded.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { java.net.URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }
            .getOrDefault(trimmed)
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun isHtmlPlayerHost(host: String): Boolean {
        val lower = host.lowercase()
        return HtmlPlayerHosts.any { lower == it || lower.endsWith(".$it") }
    }

    private fun isStreamP2pSource(name: String?, url: String): Boolean {
        if (name?.contains("StreamP2P", ignoreCase = true) == true) return true
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return false
        return host.contains("strp2p.com") || host.contains("playerp2p.com")
    }
}
