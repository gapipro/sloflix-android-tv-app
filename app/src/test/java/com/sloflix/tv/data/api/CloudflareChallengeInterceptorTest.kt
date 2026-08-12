package com.sloflix.tv.data.api

import com.sloflix.tv.ui.components.toUserMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CloudflareChallengeInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `cf-mitigated challenge header fails with a readable message`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .addHeader("cf-mitigated", "challenge")
                .setBody("<html>nope</html>"),
        )

        val error = assertThrows(CloudflareChallengeException::class.java) { call() }

        assertEquals(
            "Sloflix blocked this request (Cloudflare). Try again later.",
            error.toUserMessage("Something went wrong"),
        )
    }

    @Test
    fun `html challenge body fails instead of reaching the json parser`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .addHeader("Content-Type", "text/html; charset=UTF-8")
                .setBody("<html><title>Just a moment...</title></html>"),
        )

        assertThrows(CloudflareChallengeException::class.java) { call() }
    }

    @Test
    fun `plain api error response is passed through untouched`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"code":403,"status":"failed"}"""),
        )

        val response = call()

        assertEquals(403, response.code)
        assertEquals("""{"code":403,"status":"failed"}""", response.body?.string())
    }

    @Test
    fun `user agent is presented as a chrome android build`() {
        server.enqueue(MockResponse().setResponseCode(200))

        call().close()

        assertEquals(AndroidTvUserAgent, server.takeRequest().getHeader("User-Agent"))
    }

    private fun call() = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(CloudflareChallengeInterceptor())
        .build()
        .newCall(Request.Builder().url(server.url("/v1/media")).build())
        .execute()
}
