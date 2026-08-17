package com.sloflix.tv.ui.details

import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.EpisodeSummary
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.MediaKind
import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.model.WebViewPlaybackSource
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.ui.components.UiState
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    @Test
    fun `ready state shows resume CTA when catalog resume position is greater than zero`() =
        runTest {
            val title = titleDetails(resumePositionMs = 120_000)
            val playbackRepository = FakePlaybackRepository(Result.success(null))
            val viewModel = DetailsViewModel(
                catalogRepository = FakeCatalogRepository(detailsById = mapOf(title.id to Result.success(title))),
                playbackRepository = playbackRepository,
                sessionStore = FakeSessionStore(Session("token")),
                dispatcher = StandardTestDispatcher(testScheduler),
            )

            viewModel.load(title.id)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Ready)
            assertEquals(
                DetailsContent(
                    requestId = title.id,
                    title = title,
                    resumePositionMs = 120_000,
                ),
                (state as UiState.Ready).value,
            )
            assertTrue(state.value.canResume)
            assertEquals(0, playbackRepository.loadProgressCalls)
        }

    @Test
    fun `ready state shows resume CTA when saved progress is greater than zero`() = runTest {
        val title = titleDetails()
        val viewModel = DetailsViewModel(
            catalogRepository = FakeCatalogRepository(detailsById = mapOf(title.id to Result.success(title))),
            playbackRepository = FakePlaybackRepository(
                Result.success(
                    PlaybackProgress(
                        titleId = title.id,
                        positionMs = 93_000,
                        durationMs = 5_400_000,
                    ),
                ),
            ),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load(title.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is UiState.Ready)
        assertEquals(
            DetailsContent(
                requestId = title.id,
                title = title,
                resumePositionMs = 93_000,
            ),
            (state as UiState.Ready).value,
        )
        assertTrue(state.value.canResume)
    }

    @Test
    fun `details network failure shows offline message`() = runTest {
        val viewModel = DetailsViewModel(
            catalogRepository = FakeCatalogRepository(
                detailsById = mapOf("arrival" to Result.failure(UnknownHostException("api.sloflix.com"))),
            ),
            playbackRepository = FakePlaybackRepository(Result.success(null)),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load("arrival")
        advanceUntilIdle()

        assertEquals(
            UiState.Error("You’re offline. Check your connection and try again."),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `show details loads seasons and episodes without remapping`() = runTest {
        val show = TitleDetails(
            id = "show-1",
            name = "Demo Show",
            description = "A series",
            posterUrl = null,
            backdropUrl = null,
            year = 2024,
            genres = listOf("Drama"),
            resumePositionMs = null,
            kind = MediaKind.Show,
            seasons = listOf(1, 2),
        )
        val episodes = listOf(
            EpisodeSummary("e1", "Pilot", null, 1),
            EpisodeSummary("e2", "Next", null, 2),
        )
        val catalog = FakeCatalogRepository(
            detailsById = mapOf(show.id to Result.success(show)),
            episodesBySeason = mapOf(
                1 to Result.success(episodes),
                2 to Result.success(emptyList()),
            ),
        )
        val viewModel = DetailsViewModel(
            catalogRepository = catalog,
            playbackRepository = FakePlaybackRepository(Result.success(null)),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load(show.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as UiState.Ready
        assertEquals(1, state.value.selectedSeason)
        assertEquals(episodes, state.value.episodes)
        assertTrue(state.value.isSeries)
        assertEquals(MediaKind.Show, state.value.title.kind)
        assertEquals(listOf("show-1"), catalog.detailsCalls)

        viewModel.selectSeason(2)
        advanceUntilIdle()
        val afterSeason = (viewModel.uiState.value as UiState.Ready).value
        assertEquals(2, afterSeason.selectedSeason)
        assertTrue(afterSeason.episodes.isEmpty())
    }

    @Test
    fun `episode id loads episode details UI not remapped to show`() = runTest {
        val show = TitleDetails(
            id = "show-1",
            name = "Demo Show",
            description = "A series",
            posterUrl = "https://example.com/show.jpg",
            backdropUrl = "https://example.com/show-bg.jpg",
            year = 2024,
            genres = listOf("Drama"),
            resumePositionMs = null,
            kind = MediaKind.Show,
            seasons = listOf(1),
        )
        val episode = TitleDetails(
            id = "e1",
            name = "Pilot",
            description = "The beginning",
            posterUrl = null,
            backdropUrl = null,
            year = null,
            genres = emptyList(),
            resumePositionMs = 45_000,
            duration = "42 min",
            ratingLabel = "8.1",
            kind = MediaKind.Episode,
            season = 1,
            episodeIndex = 1,
            parentId = "show-1",
            hasExoPlayback = true,
            webViewSources = listOf(
                WebViewPlaybackSource("StreamP2P HD", "https://sf.strp2p.com/#idABC"),
            ),
        )
        val catalog = FakeCatalogRepository(
            detailsById = mapOf(
                show.id to Result.success(show),
                episode.id to Result.success(episode),
            ),
        )
        val viewModel = DetailsViewModel(
            catalogRepository = catalog,
            playbackRepository = FakePlaybackRepository(Result.success(null)),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load(episode.id)
        advanceUntilIdle()

        val content = (viewModel.uiState.value as UiState.Ready).value
        assertFalse(content.isSeries)
        assertEquals(MediaKind.Episode, content.title.kind)
        assertEquals("e1", content.title.id)
        assertEquals("1. Pilot", content.title.displayName)
        assertEquals("Demo Show", content.title.showName)
        assertEquals(2024, content.title.year)
        assertEquals(listOf("Drama"), content.title.genres)
        assertEquals("https://example.com/show.jpg", content.title.posterUrl)
        assertEquals(45_000L, content.resumePositionMs)
        assertTrue(content.canResume)
        assertEquals(
            listOf(WebViewPlaybackSource("StreamP2P HD", "https://sf.strp2p.com/#idABC")),
            content.title.webViewSources,
        )
        assertEquals(listOf("e1", "show-1"), catalog.detailsCalls)
    }

    @Test
    fun `episode resume uses playback progress when catalog has none`() = runTest {
        val episode = TitleDetails(
            id = "e1",
            name = "Pilot",
            description = "",
            posterUrl = null,
            backdropUrl = null,
            year = 2024,
            genres = emptyList(),
            resumePositionMs = null,
            kind = MediaKind.Episode,
            season = 1,
            episodeIndex = 1,
            parentId = null,
        )
        val playbackRepository = FakePlaybackRepository(
            Result.success(
                PlaybackProgress(
                    titleId = episode.id,
                    positionMs = 12_000,
                    durationMs = 2_400_000,
                ),
            ),
        )
        val viewModel = DetailsViewModel(
            catalogRepository = FakeCatalogRepository(
                detailsById = mapOf(episode.id to Result.success(episode)),
            ),
            playbackRepository = playbackRepository,
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load(episode.id)
        advanceUntilIdle()

        val content = (viewModel.uiState.value as UiState.Ready).value
        assertEquals(12_000L, content.resumePositionMs)
        assertTrue(content.canResume)
        assertEquals(1, playbackRepository.loadProgressCalls)
    }

    private fun titleDetails(resumePositionMs: Long? = null) = TitleDetails(
        id = "arrival",
        name = "Arrival",
        description = "A linguist works to communicate with visitors from another world.",
        posterUrl = "https://example.com/arrival-poster.jpg",
        backdropUrl = "https://example.com/arrival-backdrop.jpg",
        year = 2016,
        genres = listOf("Drama", "Science fiction"),
        resumePositionMs = resumePositionMs,
    )
}

private class FakeCatalogRepository(
    private val detailsById: Map<String, Result<TitleDetails>>,
    private val episodesBySeason: Map<Int, Result<List<EpisodeSummary>>> = emptyMap(),
) : CatalogRepository {
    val detailsCalls = mutableListOf<String>()

    override suspend fun details(session: Session, titleId: String): Result<TitleDetails> {
        detailsCalls += titleId
        return detailsById[titleId] ?: Result.failure(IllegalArgumentException("Unknown $titleId"))
    }

    override suspend fun categories(session: Session): Result<List<Category>> = error("Not used")

    override suspend fun titles(
        session: Session,
        categoryId: String?,
        filter: FilterState,
    ): Result<List<TitleSummary>> = error("Not used")

    override suspend fun filterOptions(session: Session): Result<FilterState> = error("Not used")

    override suspend fun continueWatching(session: Session): Result<List<TitleSummary>> =
        error("Not used")

    override suspend fun episodes(
        session: Session,
        showId: String,
        season: Int,
    ): Result<List<EpisodeSummary>> =
        episodesBySeason[season] ?: Result.success(emptyList())
}

private class FakePlaybackRepository(
    private val progressResult: Result<PlaybackProgress?>,
) : PlaybackRepository {
    var loadProgressCalls = 0

    override suspend fun loadProgress(
        session: Session,
        titleId: String,
    ): Result<PlaybackProgress?> {
        loadProgressCalls += 1
        return progressResult
    }

    override suspend fun stream(session: Session, titleId: String): Result<StreamInfo> =
        error("Not used")

    override suspend fun resolveStreamP2P(embedUrl: String): Result<StreamInfo> =
        error("Not used")

    override suspend fun saveProgress(
        session: Session,
        progress: PlaybackProgress,
    ): Result<Unit> = error("Not used")

    override suspend fun clearProgress(session: Session, titleId: String): Result<Unit> =
        error("Not used")
}

private class FakeSessionStore(
    private val session: Session?,
) : SessionStore {
    override suspend fun get(): Session? = session
    override suspend fun set(session: Session) = Unit
    override suspend fun clear() = Unit
}
