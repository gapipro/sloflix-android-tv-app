package com.sloflix.tv.ui.details

import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.PlaybackProgress
import com.sloflix.tv.domain.model.StreamInfo
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.ui.components.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    @Test
    fun `ready state shows resume CTA when catalog resume position is greater than zero`() =
        runTest {
            val title = titleDetails(resumePositionMs = 120_000)
            val viewModel = DetailsViewModel(
                catalogRepository = FakeCatalogRepository(Result.success(title)),
                playbackRepository = FakePlaybackRepository(Result.success(null)),
                sessionStore = FakeSessionStore(Session("token")),
                dispatcher = StandardTestDispatcher(testScheduler),
            )

            viewModel.load(title.id)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is UiState.Ready)
            assertEquals(
                DetailsContent(
                    title = title,
                    resumePositionMs = 120_000,
                ),
                (state as UiState.Ready).value,
            )
            assertTrue(state.value.canResume)
        }

    @Test
    fun `ready state shows resume CTA when saved progress is greater than zero`() = runTest {
        val title = titleDetails()
        val viewModel = DetailsViewModel(
            catalogRepository = FakeCatalogRepository(Result.success(title)),
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
                title = title,
                resumePositionMs = 93_000,
            ),
            (state as UiState.Ready).value,
        )
        assertTrue(state.value.canResume)
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
    private val detailsResult: Result<TitleDetails>,
) : CatalogRepository {
    override suspend fun details(session: Session, titleId: String): Result<TitleDetails> =
        detailsResult

    override suspend fun categories(session: Session): Result<List<Category>> = error("Not used")

    override suspend fun titles(
        session: Session,
        categoryId: String?,
        filter: FilterState,
    ): Result<List<TitleSummary>> = error("Not used")

    override suspend fun filterOptions(session: Session): Result<FilterState> = error("Not used")

    override suspend fun continueWatching(session: Session): Result<List<TitleSummary>> =
        error("Not used")
}

private class FakePlaybackRepository(
    private val progressResult: Result<PlaybackProgress?>,
) : PlaybackRepository {
    override suspend fun loadProgress(
        session: Session,
        titleId: String,
    ): Result<PlaybackProgress?> = progressResult

    override suspend fun stream(session: Session, titleId: String): Result<StreamInfo> =
        error("Not used")

    override suspend fun saveProgress(
        session: Session,
        progress: PlaybackProgress,
    ): Result<Unit> = error("Not used")
}

private class FakeSessionStore(
    private val session: Session?,
) : SessionStore {
    override suspend fun get(): Session? = session
    override suspend fun set(session: Session) = Unit
    override suspend fun clear() = Unit
}
