package com.sloflix.tv.data.playback

import com.sloflix.tv.domain.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resolves a StreamP2P embed URL (`https://sf.strp2p.com/#id…`) into an ExoPlayer
 * [StreamInfo] by calling `/api/v1/video`, AES-decrypting the hex body, and
 * picking an HTTP HLS URL (`source` / mirrors).
 *
 * Decrypt order: hardcoded https key/IV first; only on failure scrape player JS
 * for rotated literals and retry once.
 */
class StreamP2PClient(
    private val httpClient: OkHttpClient,
    private val json: Json = defaultJson,
    private val keySource: StreamP2PKeySource = StreamP2PKeySource(httpClient),
) {
    suspend fun resolve(embedUrl: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        runCatching { resolveBlocking(embedUrl) }
    }

    private fun resolveBlocking(embedUrl: String): StreamInfo {
        val parsed = parseEmbed(embedUrl)
        val requestUrl = parsed.origin.newBuilder()
            .addPathSegments("api/v1/video")
            .addQueryParameter("id", parsed.videoId)
            .addQueryParameter("w", "1920")
            .addQueryParameter("h", "1080")
            .addQueryParameter("r", ReferrerHost)
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .header("Referer", parsed.origin.toString())
            .header("Origin", originHeader(parsed.origin))
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "StreamP2P video API HTTP ${response.code}"
            }
            val body = response.body?.string().orEmpty()
            check(body.isNotBlank()) { "StreamP2P video API returned an empty body" }
            val payload = decryptVideoPayload(body, parsed.origin)
            val candidates = payload.streamCandidates()
            check(candidates.isNotEmpty()) { "StreamP2P response has no playable HLS URL" }
            val withTokens = candidates.map { url -> appendPlayToken(url, payload.pk) }
            val headers = mapOf(
                "Referer" to parsed.origin.toString(),
                "Origin" to originHeader(parsed.origin),
            )
            return StreamInfo(
                url = withTokens.first(),
                headers = headers,
                fallbackUrls = withTokens.drop(1),
            )
        }
    }

    /**
     * 1) Hardcoded https derivation.
     * 2) On decrypt/parse/empty-stream failure → scrape origin player JS once and retry.
     */
    private fun decryptVideoPayload(hexBody: String, origin: HttpUrl): StreamP2PVideoPayload {
        tryDecrypt(hexBody, StreamP2PCrypto.HttpsKeyUtf8, StreamP2PCrypto.HttpsIvUtf8)
            ?.let { return it }

        val scraped = keySource.fetchAndExtractKeys(originHeader(origin))
            ?: error("StreamP2P decrypt failed and no keys found in player JS")
        return tryDecrypt(hexBody, scraped.first, scraped.second)
            ?: tryDecrypt(hexBody, scraped.second, scraped.first)
            ?: error("StreamP2P decrypt failed with scraped keys")
    }

    private fun tryDecrypt(
        hexBody: String,
        keyUtf8: String,
        ivUtf8: String,
    ): StreamP2PVideoPayload? =
        runCatching {
            val plain = StreamP2PCrypto.decryptHex(
                hexCiphertext = hexBody,
                keyUtf8 = keyUtf8,
                ivUtf8 = ivUtf8,
            )
            val payload = json.decodeFromString(StreamP2PVideoPayload.serializer(), plain)
            check(payload.streamCandidates().isNotEmpty()) {
                "StreamP2P response has no playable HLS URL"
            }
            payload
        }.getOrNull()

    private fun parseEmbed(embedUrl: String): ParsedEmbed {
        val httpUrl = embedUrl.trim().toHttpUrlOrNull()
            ?: error("Invalid StreamP2P embed URL")
        val fragment = httpUrl.fragment.orEmpty()
        val videoId = fragment.substringBefore('&').trim().takeIf { it.length > 1 }
            ?: error("No videoId in StreamP2P embed hash")
        val origin = HttpUrl.Builder()
            .scheme(httpUrl.scheme)
            .host(httpUrl.host)
            .port(httpUrl.port)
            .build()
        return ParsedEmbed(origin = origin, videoId = videoId)
    }

    private fun appendPlayToken(url: String, pk: StreamP2PPk?): String {
        if (pk == null || pk.k.isNullOrBlank()) return url
        if (!url.contains("/v4/")) return url
        val parsed = url.toHttpUrlOrNull() ?: return url
        return parsed.newBuilder()
            .setQueryParameter("k", pk.k)
            .apply {
                pk.kx?.let { setQueryParameter("kx", it.toString()) }
            }
            .build()
            .toString()
    }

    private fun originHeader(url: HttpUrl): String = buildString {
        append(url.scheme)
        append("://")
        append(url.host)
        val defaultPort = if (url.scheme == "https") 443 else 80
        if (url.port != defaultPort) {
            append(':')
            append(url.port)
        }
    }

    private data class ParsedEmbed(
        val origin: HttpUrl,
        val videoId: String,
    )

    @Serializable
    internal data class StreamP2PVideoPayload(
        val source: String? = null,
        val cf: String? = null,
        @SerialName("cfNative") val cfNative: String? = null,
        val hlsVideoTiktok: String? = null,
        val hlsVideoGoogle: String? = null,
        val pk: StreamP2PPk? = null,
    ) {
        /**
         * Prefer in-house HTTP HLS (`source`) — Cloudflare mirrors often challenge
         * non-browser clients. Then Google / TikTok mirrors, then CF.
         */
        fun streamCandidates(): List<String> =
            listOfNotNull(source, hlsVideoGoogle, hlsVideoTiktok, cfNative, cf)
                .map { it.trim() }
                .filter { it.startsWith("http://") || it.startsWith("https://") }
                .distinct()
    }

    @Serializable
    internal data class StreamP2PPk(
        val k: String? = null,
        val kx: Long? = null,
    )

    private companion object {
        /** Matches the player `ge()` value when embedded from Sloflix. */
        const val ReferrerHost = "sloflix.com"

        val defaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
