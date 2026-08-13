package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.MediaSourceDto
import com.sloflix.tv.domain.model.SubtitleTrack
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
 * never be offered as candidates.
 */
internal object StreamSourceResolver {
    private const val PlayerHost = "player.sloflix.com"
    private const val SubtitleBaseUrl = "https://www.sloflix.com/subtitles/"

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
}
