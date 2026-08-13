package com.sloflix.tv.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.domain.model.ContinueWatchingEntry
import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.playback.InMemoryContinueWatchingStore
import com.sloflix.tv.domain.session.Session
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

class PlaybackRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryContinueWatchingStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemoryContinueWatchingStore()
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
                            "media_source_name": "SLOSubs",
                            "subtitle_location": "film.vtt"
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
        val repository = PlaybackRepositoryImpl(api(), MutableSessionProvider(), store)

        val stream = repository.stream(Session("fake-token"), "1").getOrThrow()

        assertEquals("https://cdn.example.com/master.m3u8", stream.url)
        assertEquals(listOf("https://cdn.example.com/arrival.mp4"), stream.fallbackUrls)
        assertEquals("https://player.sloflix.com/", stream.headers["Referer"])
        assertEquals("https://player.sloflix.com", stream.headers["Origin"])
        assertEquals("https://www.sloflix.com/subtitles/film.vtt", stream.subtitles.single().url)
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
        val repository = PlaybackRepositoryImpl(api(), MutableSessionProvider(), store)

        val result = repository.stream(Session("fake-token"), "1")

        assertEquals("No playback source is available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `save progress upserts continue watching metadata`() = runTest {
        server.enqueue(successResponse("""{"code":200,"status":"success"}"""))
        server.enqueue(
            successResponse(
                """
                {
                  "code": 200,
                  "status": "success",
                  "data": {
                    "media_id": 42,
                    "media_name": "Arrival",
                    "media_thumbnail_url": "https://cdn.example.com/poster.jpg"
                  }
                }
                """.trimIndent(),
            ),
        )
        val repository = PlaybackRepositoryImpl(
            api = api(),
            sessionProvider = MutableSessionProvider(),
            continueWatchingStore = store,
            clockMs = { 1_700_000_000_000L },
        )

        repository.saveProgress(
            Session("token"),
            PlaybackProgress(titleId = "42", positionMs = 600_000, durationMs = 7_200_000),
        ).getOrThrow()

        val entry = checkNotNull(store.get("42"))
        assertEquals("Arrival", entry.name)
        assertEquals("https://cdn.example.com/poster.jpg", entry.posterUrl)
        assertEquals(600_000L, entry.positionMs)
        assertEquals(7_200_000L, entry.durationMs)
        assertEquals(1_700_000_000_000L, entry.updatedAtMs)
    }

    @Test
    fun `clear progress posts zero watch time and removes local entry`() = runTest {
        store.upsert(
            ContinueWatchingEntry(
                titleId = "42",
                name = "Arrival",
                posterUrl = null,
                positionMs = 600_000,
                durationMs = 7_200_000,
                updatedAtMs = 1L,
            ),
        )
        server.enqueue(successResponse("""{"code":200,"status":"success"}"""))
        val repository = PlaybackRepositoryImpl(api(), MutableSessionProvider(), store)

        repository.clearProgress(Session("token"), "42").getOrThrow()

        val request = server.takeRequest()
        assertEquals("/v1/media/42/player/metadata", request.path)
        assertTrue(request.body.readUtf8().contains("\"watch_time\":0.0"))
        assertEquals(null, store.get("42"))
    }

    private fun api(): SloflixApi {
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SloflixApi::class.java)
    }

    private fun successResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}
