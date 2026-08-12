package com.sloflix.tv.data.api

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionProvider: SessionProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val session = sessionProvider.session() ?: return chain.proceed(request)
        val authenticated = request.newBuilder()
            .header("Authorization", "Bearer ${session.accessToken}")
            .apply {
                session.cookieHeader?.let { header("Cookie", it) }
            }
            .build()
        return chain.proceed(authenticated)
    }
}
