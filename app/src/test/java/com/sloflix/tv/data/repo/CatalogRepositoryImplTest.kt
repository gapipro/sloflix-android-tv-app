package com.sloflix.tv.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.domain.model.ContinueWatchingEntry
import com.sloflix.tv.domain.model.FilterState
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

class CatalogRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var continueWatchingStore: InMemoryContinueWatchingStore
    private lateinit var repository: CatalogRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        continueWatchingStore = InMemoryContinueWatchingStore()
        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SloflixApi::class.java)
        repository = CatalogRepositoryImpl(api, MutableSessionProvider(), continueWatchingStore)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `titles maps all documented filter query parameters`() = runTest {
        server.enqueue(successResponse("""{"code":200,"status":"success","data":[]}"""))
        val filter = FilterState(
            selectedGenreIds = setOf("11"),
            query = "space opera",
            selectedType = 2,
            sortBy = 6,
            limit = 25,
            offset = 50,
        )

        val result = repository.titles(Session("token"), "1", filter)

        assertTrue(result.isSuccess)
        val url = checkNotNull(server.takeRequest().requestUrl)
        assertEquals("/v1/media", url.encodedPath)
        assertEquals("6", url.queryParameter("sortBy"))
        assertEquals("11,1", url.queryParameter("genres"))
        assertEquals("2", url.queryParameter("type"))
        assertEquals("space opera", url.queryParameter("query"))
        assertEquals("25", url.queryParameter("limit"))
        assertEquals("50", url.queryParameter("offset"))
    }

    @Test
    fun `filter options merges genres with documented type and sort choices`() = runTest {
        server.enqueue(
            successResponse(
                """
                {
                  "code": 200,
                  "status": "success",
                  "data": [{"genre_id": 11, "genre_name": "Comedy"}]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.filterOptions(Session("token")).getOrThrow()

        assertEquals(listOf("11" to "Comedy"), result.availableGenres)
        assertEquals(listOf(1 to "Filmi", 2 to "Serije"), result.availableTypes)
        assertEquals(
            listOf(
                1 to "Newest added",
                2 to "Oldest added",
                3 to "Highest rating",
                4 to "Year descending",
                5 to "Year ascending",
                6 to "Most watched",
                7 to "Relevance",
            ),
            result.availableSorts,
        )
    }

    @Test
    fun `titles maps created_at under 7 days as isNew`() = runTest {
        val recent = java.time.LocalDateTime.now().minusDays(2)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val old = java.time.LocalDateTime.now().minusDays(10)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        server.enqueue(
            successResponse(
                """
                {
                  "code": 200,
                  "status": "success",
                  "data": [
                    {
                      "media_id": 1,
                      "media_name": "Fresh",
                      "media_thumbnail_url": "https://example.com/a.jpg",
                      "created_at": "$recent"
                    },
                    {
                      "media_id": 2,
                      "media_name": "Old",
                      "media_thumbnail_url": "https://example.com/b.jpg",
                      "created_at": "$old"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val titles = repository.titles(Session("token"), null, FilterState()).getOrThrow()

        assertEquals(true, titles[0].isNew)
        assertEquals(false, titles[1].isNew)
    }

    @Test
    fun `continue watching keeps only titles watched at least ten minutes`() = runTest {
        continueWatchingStore.upsert(
            ContinueWatchingEntry(
                titleId = "short",
                name = "Too short",
                posterUrl = null,
                positionMs = ContinueWatchingEntry.MinResumePositionMs - 1,
                durationMs = 7_200_000,
                updatedAtMs = 20,
            ),
        )
        continueWatchingStore.upsert(
            ContinueWatchingEntry(
                titleId = "ready",
                name = "Ready",
                posterUrl = "https://example.com/ready.jpg",
                positionMs = ContinueWatchingEntry.MinResumePositionMs,
                durationMs = 7_200_000,
                updatedAtMs = 10,
            ),
        )
        continueWatchingStore.upsert(
            ContinueWatchingEntry(
                titleId = "newer",
                name = "Newer",
                posterUrl = null,
                positionMs = ContinueWatchingEntry.MinResumePositionMs + 1_000,
                durationMs = 3_600_000,
                updatedAtMs = 30,
            ),
        )

        val titles = repository.continueWatching(Session("token")).getOrThrow()

        assertEquals(listOf("newer", "ready"), titles.map { it.id })
        assertEquals(601_000f / 3_600_000f, titles[0].progressFraction!!, 0.0001f)
    }

    private fun successResponse(body: String) =
        MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(body)
}
