package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.MediaSourceDto
import java.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Turns `media_sources` into an ordered list of playable URLs.
 *
 * Sloflix hands out `https://player.sloflix.com/...` HTML pages that wrap a signed upstream URL in
 * their query string. The exact parameter name is not documented and could not be captured without
 * a live signed URL (the API spec redacts them), so every query value of the pages is inspected and
 * the first one that is itself an `http(s)` URL — or a base64-encoded one — is treated as the
 * upstream media URL. [UpstreamQueryKeys] are checked first because they are the names such players
 * conventionally use (`source`, `url`, `file`, `src`, …); if none match, any URL-shaped value wins.
 *
 * The player page itself is kept as a last-resort candidate: ExoPlayer cannot render HTML, but
 * keeping it means a title with only wrapper sources still produces a candidate to fail loudly on
 * instead of an empty list.
 */
internal object StreamSourceResolver {
    private const val PlayerHost = "player.sloflix.com"

    private val UpstreamQueryKeys = listOf(
        "source", "url", "file", "src", "link", "video", "stream", "m3u8", "mp4", "playlist",
        "media", "target", "hls",
    )

    private val DirectMediaExtensions = listOf(
        ".m3u8", ".mpd", ".mp4", ".m4v", ".mkv", ".webm", ".ts",
    )

    /**
     * Candidates ordered by how likely ExoPlayer can play them: direct media URLs first, then
     * anything unwrapped from a player page, then the wrapper pages themselves. Sources whose URL is
     * blank or not `http(s)` are unusable and dropped.
     */
    fun candidates(sources: List<MediaSourceDto>): List<String> =
        sources.mapNotNull { source ->
            source.url.trim().takeIf { it.isNotEmpty() }?.toHttpUrlOrNull()
        }.flatMap { url ->
            if (url.host.equals(PlayerHost, ignoreCase = true)) {
                buildList {
                    upstreamUrl(url)?.let { add(Candidate(it, isWrapper = false)) }
                    add(Candidate(url.toString(), isWrapper = true))
                }
            } else {
                listOf(Candidate(url.toString(), isWrapper = false))
            }
        }.sortedWith(
            compareBy(
                { it.isWrapper },
                { !isDirectMedia(it.url) },
            ),
        ).map { it.url }.distinct()

    private fun upstreamUrl(playerUrl: HttpUrl): String? {
        val named = UpstreamQueryKeys.firstNotNullOfOrNull { key ->
            playerUrl.queryParameterNames
                .firstOrNull { it.equals(key, ignoreCase = true) }
                ?.let(playerUrl::queryParameter)
                ?.let(::asMediaUrl)
        }
        if (named != null) return named
        return playerUrl.queryParameterNames
            .asSequence()
            .mapNotNull { playerUrl.queryParameter(it) }
            .firstNotNullOfOrNull(::asMediaUrl)
    }

    private fun asMediaUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        directUrl(trimmed)?.let { return it }
        return decodeBase64(trimmed)?.let(::directUrl)
    }

    private fun directUrl(value: String): String? = value
        .takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        ?.toHttpUrlOrNull()
        ?.takeUnless { it.host.equals(PlayerHost, ignoreCase = true) }
        ?.toString()

    private fun decodeBase64(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value.trimEnd('=')))
    }.getOrNull()

    private fun isDirectMedia(url: String): Boolean {
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase() ?: return false
        return DirectMediaExtensions.any(path::endsWith)
    }

    private data class Candidate(val url: String, val isWrapper: Boolean)
}
