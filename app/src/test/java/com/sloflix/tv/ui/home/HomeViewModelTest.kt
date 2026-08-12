package com.sloflix.tv.ui.home

import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.ui.components.UiState
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
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            UiState.Ready(
                HomeContent(
                    rows = listOf(
                        HomeRow(drama.name, dramaTitles),
                        HomeRow(comedy.name, comedyTitles),
                    ),
                ),
            ),
            viewModel.uiState.value,
        )
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
            sessionStore = FakeSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value as UiState.Ready
        assertEquals("Continue watching", state.value.rows.first().title)
        assertEquals(resumeTitles, state.value.rows.first().titles)
        assertFalse(state.value.rows.drop(1).any { it.title == "Continue watching" })
    }

    @Test
    fun `load failure shows error and retry loads again`() = runTest {
        val category = Category("drama", "Drama")
        val repository = FakeCatalogRepository(
            categoryResult = Result.failure(IllegalStateException("Catalog unavailable")),
        )
        val viewModel = HomeViewModel(
            catalogRepository = repository,
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
        assertEquals(2, repository.categoryCalls)
    }

    private fun title(id: String, name: String) = TitleSummary(
        id = id,
        name = name,
        posterUrl = "https://example.com/$id.jpg",
    )
}

private class FakeCatalogRepository(
    var categoryResult: Result<List<Category>>,
    var titlesByCategory: Map<String, Result<List<TitleSummary>>> = emptyMap(),
    private val continueWatchingResult: Result<List<TitleSummary>> = Result.success(emptyList()),
) : CatalogRepository {
    var categoryCalls = 0

    override suspend fun categories(session: Session): Result<List<Category>> {
        categoryCalls += 1
        return categoryResult
    }

    override suspend fun titles(
        session: Session,
        categoryId: String?,
        filter: FilterState,
    ): Result<List<TitleSummary>> = titlesByCategory.getValue(requireNotNull(categoryId))

    override suspend fun continueWatching(session: Session): Result<List<TitleSummary>> =
        continueWatchingResult

    override suspend fun filterOptions(session: Session): Result<FilterState> =
        error("Not used")

    override suspend fun details(session: Session, titleId: String): Result<TitleDetails> =
        error("Not used")
}

private class FakeSessionStore(
    private val session: Session?,
) : SessionStore {
    override suspend fun get(): Session? = session
    override suspend fun set(session: Session) = Unit
    override suspend fun clear() = Unit
}
