package com.sloflix.tv.data.api

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Raised when Cloudflare answers with a bot challenge instead of the API response. It extends
 * [IOException] so callers treat it like any other transport failure and never as an auth failure:
 * the session is still fine, the request just never reached Sloflix.
 */
class CloudflareChallengeException(
    override val message: String = ChallengeMessage,
) : IOException(message) {
    companion object {
        const val ChallengeMessage = "Sloflix blocked this request (Cloudflare). Try again later."
    }
}

/**
 * Detects Cloudflare interstitials, which arrive either as a `cf-mitigated: challenge` header or as
 * an HTML "Just a moment…" page under a 403/503, and converts them into a readable failure instead
 * of letting the HTML reach the JSON parser as a serialization dump.
 */
class CloudflareChallengeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true) {
            response.close()
            throw CloudflareChallengeException()
        }
        if (response.code !in ChallengeCodes) return response
        val contentType = response.header("Content-Type").orEmpty()
        if (!contentType.contains("html", ignoreCase = true)) return response
        val body = response.peekBody(PeekBytes).string()
        if (ChallengeMarkers.none { body.contains(it, ignoreCase = true) }) return response
        response.close()
        throw CloudflareChallengeException()
    }

    private companion object {
        val ChallengeCodes = setOf(403, 429, 503)
        val ChallengeMarkers = listOf(
            "cf-browser-verification",
            "cf_chl_opt",
            "challenge-platform",
            "Just a moment",
            "Attention Required",
            "Checking your browser",
        )
        const val PeekBytes = 64L * 1024
    }
}
