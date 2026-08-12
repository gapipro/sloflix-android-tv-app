package com.sloflix.tv.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class AuthRepositoryImplTest {
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
    fun `login maps token and response cookie into session`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("Set-Cookie", "sloflix=fake-cookie; Path=/; HttpOnly")
                .setBody(
                    """
                    {
                      "code": 200,
                      "message": "Logged in",
                      "metadata": {"access_token": "fake.header.signature"},
                      "status": "success"
                    }
                    """.trimIndent(),
                ),
        )
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SloflixApi::class.java)
        val sessionProvider = MutableSessionProvider()
        val repository = AuthRepositoryImpl(api, sessionProvider)

        val result = repository.login("tester", "fake-password")

        assertTrue(result.isSuccess)
        assertEquals("fake.header.signature", result.getOrThrow().accessToken)
        assertEquals("sloflix=fake-cookie", result.getOrThrow().cookieHeader)
        assertEquals(result.getOrThrow(), sessionProvider.session())
        val request = server.takeRequest()
        assertEquals("/v1/user/login", request.path)
        assertEquals(
            """{"username":"tester","password":"fake-password"}""",
            request.body.readUtf8(),
        )
    }
}
