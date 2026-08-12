package com.sloflix.tv.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.data.net.NetworkStatus
import com.sloflix.tv.domain.repo.SessionValidity
import com.sloflix.tv.domain.session.Session
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
        val sessionProvider = MutableSessionProvider()
        val repository = AuthRepositoryImpl(api(), sessionProvider)

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

    @Test
    fun `login rejected as HTTP 500 failed envelope reports bad credentials`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"code":500,"status":"failed","message":"Internal Server Error"}"""),
        )
        val repository = AuthRepositoryImpl(api(), MutableSessionProvider())

        val result = repository.login("tester", "wrong-fake-password")

        assertEquals(
            "Incorrect username or password.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `failed server validation clears rejected session`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"code":500,"status":"failed"}"""),
        )
        val sessionProvider = MutableSessionProvider()
        val repository = AuthRepositoryImpl(api(), sessionProvider)

        assertEquals(SessionValidity.Invalid, repository.validateSession(Session(futureToken())))
        assertEquals(null, sessionProvider.session())
    }

    @Test
    fun `expired token is invalid without contacting the server`() = runTest {
        val sessionProvider = MutableSessionProvider()
        val repository = AuthRepositoryImpl(api(), sessionProvider)

        assertEquals(SessionValidity.Invalid, repository.validateSession(Session(expiredToken())))
        assertEquals(null, sessionProvider.session())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `unreachable server leaves the session untouched`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val sessionProvider = MutableSessionProvider()
        val repository = AuthRepositoryImpl(api(), sessionProvider)
        val session = Session(futureToken())

        assertEquals(SessionValidity.Unverified, repository.validateSession(session))
        assertEquals(session, sessionProvider.session())
    }

    @Test
    fun `offline device skips the preference check and keeps the session`() = runTest {
        val sessionProvider = MutableSessionProvider()
        val repository = AuthRepositoryImpl(
            api = api(),
            sessionProvider = sessionProvider,
            networkStatus = NetworkStatus { false },
        )
        val session = Session(futureToken())

        assertEquals(SessionValidity.Unverified, repository.validateSession(session))
        assertEquals(session, sessionProvider.session())
        assertEquals(0, server.requestCount)
    }

    private fun api(): SloflixApi {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SloflixApi::class.java)
    }

    private fun futureToken(): String = token(System.currentTimeMillis() / 1_000 + 3_600)

    private fun expiredToken(): String = token(System.currentTimeMillis() / 1_000 - 60)

    private fun token(expirySeconds: Long): String {
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":$expirySeconds}""".toByteArray())
        return "header.$encoded.signature"
    }
}
