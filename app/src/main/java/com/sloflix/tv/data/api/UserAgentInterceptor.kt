package com.sloflix.tv.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cloudflare challenges non-browser clients, and OkHttp's default `okhttp/4.x` agent is an obvious
 * tell, so every request presents a Chrome-on-Android-TV agent instead.
 */
const val AndroidTvUserAgent: String =
    "Mozilla/5.0 (Linux; Android 12; BRAVIA 4K GB) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 CrKey/1.54.250320"

class UserAgentInterceptor(
    private val userAgent: String = AndroidTvUserAgent,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}
