package com.sloflix.tv.ui.home

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
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `load creates a row for each category`() = runTest {
        val drama = Category("drama", "Drama")
        val comedy = Category("comedy", "Comedy")
        val dramaTitles = listOf(title("arrival", "Arrival"))
        val comedyTitles = listOf(title("paddington", "Paddington"))
        val viewModel = HomeViewModel(
            catalogRepository = FakeCatalogRepository(
                categoryResult = Result.success(listOf(drama, comedy)),
                titlesByCategory = mapOf(
                    drama.id to Result.success(dramaTitles),
                    comedy.id to Result.success(comedyTitles),
                ),
            ),
            playbackRepository = FakePlaybackRepository(),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            UiState.Ready(
                HomeContent(
                    categories = listOf(drama, comedy),
                    selectedCategoryId = null,
                    rows = listOf(
                        HomeRow(drama.name, dramaTitles),
                        HomeRow(comedy.name, comedyTitles),
                    ),
                ),
            ),
            viewModel.uiState.value,
        )
        assertEquals(1, viewModel.filterState.value.sortBy)
    }

    @Test
    fun `selecting a category reloads that genre feed`() = runTest {
        val drama = Category("drama", "Drama")
        val comedy = Category("comedy", "Comedy")
        val dramaTitles = listOf(title("arrival", "Arrival"))
        val repository = FakeCatalogRepository(
            categoryResult = Result.success(listOf(drama, comedy)),
            titlesByCategory = mapOf(
                drama.id to Result.success(dramaTitles),
                comedy.id to Result.success(emptyList()),
            ),
        )
        val viewModel = HomeViewModel(
            catalogRepository = repository,
            playbackRepository = FakePlaybackRepository(),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()
        viewModel.selectCategory(drama.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value as UiState.Ready
        assertEquals(drama.id, state.value.selectedCategoryId)
        assertEquals(listOf(HomeRow(drama.name, dramaTitles)), state.value.rows)
        assertEquals(setOf(drama.id), viewModel.filterState.value.selectedGenreIds)
    }

    @Test
    fun `continue watching is first only when non-empty`() = runTest {
        val category = Category("drama", "Drama")
        val resumeTitles = listOf(title("resume", "Resume me"))
        val repository = FakeCatalogRepository(
            categoryResult = Result.success(listOf(category)),
            titlesByCategory = mapOf(category.id to Result.success(emptyList())),
            continueWatchingResult = Result.success(resumeTitles),
        )
        val viewModel = HomeViewModel(
            catalogRepository = repository,
            playbackRepository = FakePlaybackRepository(),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value as UiState.Ready
        assertEquals(HomeRowKeys.ContinueWatching, state.value.rows.first().title)
        assertEquals(resumeTitles, state.value.rows.first().titles)
        assertFalse(state.value.rows.drop(1).any { it.title == HomeRowKeys.ContinueWatching })
    }

    @Test
    fun `remove from continue watching clears progress and drops the poster`() = runTest {
        val category = Category("drama", "Drama")
        val resumeTitles = listOf(
            title("keep", "Keep me"),
            title("drop", "Drop me"),
        )
        val playback = FakePlaybackRepository()
        val viewModel = HomeViewModel(
            catalogRepository = FakeCatalogRepository(
                categoryResult = Result.success(listOf(category)),
                titlesByCategory = mapOf(category.id to Result.success(emptyList())),
                continueWatchingResult = Result.success(resumeTitles),
            ),
            playbackRepository = playback,
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()
        viewModel.removeFromContinueWatching("drop")
        advanceUntilIdle()

        assertEquals(listOf("drop"), playback.clearedTitleIds)
        val state = viewModel.uiState.value as UiState.Ready
        assertEquals(
            listOf(title("keep", "Keep me")),
            state.value.rows.first { it.title == HomeRowKeys.ContinueWatching }.titles,
        )
    }

    @Test
    fun `load failure shows error and retry loads again`() = runTest {
        val category = Category("drama", "Drama")
        val repository = FakeCatalogRepository(
            categoryResult = Result.failure(IllegalStateException("Catalog unavailable")),
        )
        val viewModel = HomeViewModel(
            catalogRepository = repository,
            playbackRepository = FakePlaybackRepository(),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(UiState.Error("Catalog unavailable"), viewModel.uiState.value)

        repository.categoryResult = Result.success(listOf(category))
        repository.titlesByCategory = mapOf(category.id to Result.success(emptyList()))
        viewModel.retry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UiState.Ready)
        assertEquals(2, repository.filterOptionsCalls)
    }

    @Test
    fun `load network failure shows offline message`() = runTest {
        val viewModel = HomeViewModel(
            catalogRepository = FakeCatalogRepository(
                categoryResult = Result.failure(IOException("socket closed")),
            ),
            playbackRepository = FakePlaybackRepository(),
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            UiState.Error("You’re offline. Check your connection and try again."),
            viewModel.uiState.value,
        )
    }

    private fun title(id: String, name: String) = TitleSummary(
        id = id,
        name = name,
        posterUrl = "https://example.com/$id.jpg",
    )
}

private class FakeCatalogRepository(
    var categoryResult: Result<List<Category>>,
    var titlesByCategory: Map<String?, Result<List<TitleSummary>>> = emptyMap(),
    private val continueWatchingResult: Result<List<TitleSummary>> = Result.success(emptyList()),
) : CatalogRepository {
    var filterOptionsCalls = 0

    override suspend fun categories(session: Session): Result<List<Category>> = categoryResult

    override suspend fun titles(
        session: Session,
        categoryId: String?,
        filter: FilterState,
    ): Result<List<TitleSummary>> = titlesByCategory.getValue(categoryId)

    override suspend fun continueWatching(session: Session): Result<List<TitleSummary>> =
        continueWatchingResult

    override suspend fun filterOptions(session: Session): Result<FilterState> {
        filterOptionsCalls += 1
        return categoryResult.map { categories ->
            FilterState(availableGenres = categories.map { it.id to it.name })
        }
    }

    override suspend fun details(session: Session, titleId: String): Result<TitleDetails> =
        error("Not used")

    override suspend fun episodes(
        session: Session,
        showId: String,
        season: Int,
    ): Result<List<com.sloflix.tv.domain.model.EpisodeSummary>> = Result.success(emptyList())
}

private class FakePlaybackRepository : PlaybackRepository {
    val clearedTitleIds = mutableListOf<String>()

    override suspend fun stream(session: Session, titleId: String): Result<StreamInfo> =
        error("Not used")

    override suspend fun saveProgress(
        session: Session,
        progress: PlaybackProgress,
    ): Result<Unit> = error("Not used")

    override suspend fun loadProgress(
        session: Session,
        titleId: String,
    ): Result<PlaybackProgress?> = error("Not used")

    override suspend fun clearProgress(session: Session, titleId: String): Result<Unit> {
        clearedTitleIds += titleId
        return Result.success(Unit)
    }
}

private class FakeSessionStore(
    private val session: Session?,
) : SessionStore {
    override suspend fun get(): Session? = session
    override suspend fun set(session: Session) = Unit
    override suspend fun clear() = Unit
}
