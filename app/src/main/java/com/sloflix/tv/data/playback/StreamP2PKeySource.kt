package com.sloflix.tv.data.playback

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches StreamP2P player origin HTML → `/assets/index-*.js` and extracts
 * AES-CBC key/IV string literals when hardcoded [StreamP2PCrypto] values fail.
 */
open class StreamP2PKeySource(
    private val httpClient: OkHttpClient,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val cacheTtlMs: Long = DefaultCacheTtlMs,
) {
    private data class CacheEntry(
        val keys: Pair<String, String>?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * GET `{origin}/`, locate the Vite `assets/index-….js` script, GET that
     * bundle, and extract a key/IV pair. Results are cached per origin (including
     * null after a failed extract) for [cacheTtlMs].
     */
    open fun fetchAndExtractKeys(origin: String): Pair<String, String>? {
        val originUrl = origin.trim().trimEnd('/').toHttpUrlOrNull()
            ?: return null
        val cacheKey = originHeader(originUrl)
        val now = nowMs()
        cache[cacheKey]?.let { entry ->
            if (entry.expiresAtMs > now) return entry.keys
        }
        val extracted = runCatching { fetchAndExtractKeysUncached(originUrl) }.getOrNull()
        cache[cacheKey] = CacheEntry(keys = extracted, expiresAtMs = now + cacheTtlMs)
        return extracted
    }

    private fun fetchAndExtractKeysUncached(origin: HttpUrl): Pair<String, String>? {
        val html = getBody(
            origin.newBuilder().addPathSegment("").build().toString().trimEnd('/') + "/",
            referer = originHeader(origin),
        ) ?: return null
        val scriptSrc = findAssetIndexScriptSrc(html) ?: return null
        val scriptUrl = resolveScriptUrl(origin, scriptSrc) ?: return null
        val js = getBody(scriptUrl, referer = originHeader(origin)) ?: return null
        return extractKeysFromJs(js)
    }

    private fun getBody(url: String, referer: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    private fun resolveScriptUrl(origin: HttpUrl, scriptSrc: String): String? {
        if (scriptSrc.startsWith("http://") || scriptSrc.startsWith("https://")) {
            return scriptSrc
        }
        val path = if (scriptSrc.startsWith("/")) scriptSrc.drop(1) else scriptSrc
        return origin.newBuilder()
            .addPathSegments(path)
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

    companion object {
        private const val DefaultCacheTtlMs = 5 * 60 * 1000L

        private val AssetIndexSrcRegex =
            Regex("""src=["']([^"']*/assets/index-[^"']+\.js)["']""", RegexOption.IGNORE_CASE)

        /** 16-char ASCII alphanumeric string literals (AES-128 key/IV candidates). */
        private val SixteenCharLiteralRegex =
            Regex("""["']([A-Za-z0-9]{16})["']""")

        private val AesCbcTokenRegex =
            Regex("""AES-CBC""", RegexOption.IGNORE_CASE)

        /** `iv: "…"`, `iv:enc("…")`, `{name:"AES-CBC",iv:…}`. */
        private val IvContextRegex =
            Regex(
                """iv\s*[:=]\s*(?:[A-Za-z_$][\w$]*\s*\(\s*)?["']([A-Za-z0-9]{16})["']""",
                RegexOption.IGNORE_CASE,
            )

        /** `importKey("…")` / `importKey('raw', "…")`-adjacent raw key material. */
        private val ImportKeyLiteralRegex =
            Regex(
                """importKey\s*\(\s*(?:["']raw["']\s*,\s*)?["']([A-Za-z0-9]{16})["']""",
                RegexOption.IGNORE_CASE,
            )

        fun findAssetIndexScriptSrc(html: String): String? =
            AssetIndexSrcRegex.find(html)?.groupValues?.getOrNull(1)

        /**
         * Prefer hardcoded https key/IV when both appear as literals; otherwise pick
         * a plausible pair of distinct 16-char literals near `AES-CBC` usage, or any
         * two distinct candidates as a last resort.
         */
        fun extractKeysFromJs(js: String): Pair<String, String>? {
            val literals = SixteenCharLiteralRegex.findAll(js)
                .map { it.groupValues[1] }
                .toList()
            if (literals.isEmpty()) return null

            val knownKey = StreamP2PCrypto.HttpsKeyUtf8
            val knownIv = StreamP2PCrypto.HttpsIvUtf8
            if (knownKey in literals && knownIv in literals) {
                return knownKey to knownIv
            }

            val contextualIv = IvContextRegex.findAll(js).map { it.groupValues[1] }.toSet()
            val contextualKey = ImportKeyLiteralRegex.findAll(js).map { it.groupValues[1] }.toSet()
            for (key in contextualKey) {
                for (iv in contextualIv) {
                    if (key != iv) return key to iv
                }
            }
            if (contextualKey.size == 1 && contextualIv.isEmpty()) {
                val key = contextualKey.first()
                val iv = literalsNearAesCbc(js).firstOrNull { it != key }
                    ?: literals.firstOrNull { it != key }
                if (iv != null) return key to iv
            }
            if (contextualIv.size == 1 && contextualKey.isEmpty()) {
                val iv = contextualIv.first()
                val key = literalsNearAesCbc(js).firstOrNull { it != iv }
                    ?: literals.firstOrNull { it != iv }
                if (key != null) return key to iv
            }

            val nearAes = literalsNearAesCbc(js)
            pickDistinctPair(nearAes)?.let { return it }
            return pickDistinctPair(literals)
        }

        private fun literalsNearAesCbc(js: String): List<String> {
            val window = 400
            val found = mutableListOf<String>()
            for (match in AesCbcTokenRegex.findAll(js)) {
                val start = (match.range.first - window).coerceAtLeast(0)
                val end = (match.range.last + window + 1).coerceAtMost(js.length)
                val slice = js.substring(start, end)
                SixteenCharLiteralRegex.findAll(slice).forEach { found += it.groupValues[1] }
            }
            return found
        }

        private fun pickDistinctPair(candidates: List<String>): Pair<String, String>? {
            val unique = candidates.distinct()
            if (unique.size < 2) return null
            // Prefer known constants when only one of them is present alongside another.
            val knownKey = StreamP2PCrypto.HttpsKeyUtf8
            val knownIv = StreamP2PCrypto.HttpsIvUtf8
            when {
                knownKey in unique && knownIv !in unique -> {
                    val other = unique.first { it != knownKey }
                    return knownKey to other
                }
                knownIv in unique && knownKey !in unique -> {
                    val other = unique.first { it != knownIv }
                    return other to knownIv
                }
            }
            return unique[0] to unique[1]
        }
    }
}
