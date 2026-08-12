package com.sloflix.tv.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.domain.session.Session
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class PlaybackRepositoryImplTest {
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
    fun `stream prefers a direct media url and keeps the rest as fallbacks`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "code": 200,
                      "status": "success",
                      "data": {
                        "media_id": 1,
                        "media_name": "Arrival",
                        "media_sources": [
                          {
                            "media_source": "https://player.sloflix.com/embed?source=https%3A%2F%2Fcdn.example.com%2Fmaster.m3u8",
                            "media_source_name": "SLOSubs"
                          },
                          {
                            "media_source": "https://cdn.example.com/arrival.mp4",
                            "media_source_name": "Mirror"
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
        )
        val repository = PlaybackRepositoryImpl(api(), MutableSessionProvider())

        val stream = repository.stream(Session("fake-token"), "1").getOrThrow()

        assertEquals("https://cdn.example.com/master.m3u8", stream.url)
        assertEquals(
            listOf(
                "https://cdn.example.com/arrival.mp4",
                "https://player.sloflix.com/embed?source=https%3A%2F%2Fcdn.example.com%2Fmaster.m3u8",
            ),
            stream.fallbackUrls,
        )
    }

    @Test
    fun `stream fails when no source is usable`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "code": 200,
                      "status": "success",
                      "data": {
                        "media_id": 1,
                        "media_name": "Arrival",
                        "media_sources": [{"media_source": "  "}]
                      }
                    }
                    """.trimIndent(),
                ),
        )
        val repository = PlaybackRepositoryImpl(api(), MutableSessionProvider())

        val result = repository.stream(Session("fake-token"), "1")

        assertEquals("No playback source is available", result.exceptionOrNull()?.message)
    }

    private fun api(): SloflixApi {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SloflixApi::class.java)
    }
}
