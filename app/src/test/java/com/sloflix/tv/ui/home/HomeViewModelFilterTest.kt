package com.sloflix.tv.ui.home

import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.session.Session
import com.sloflix.tv.domain.session.SessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFilterTest {
    @Test
    fun `selecting a genre updates filter and reloads titles`() = runTest {
        val repository = FilterCatalogRepository()
        val viewModel = HomeViewModel(
            catalogRepository = repository,
            sessionStore = FilterSessionStore(Session("token")),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        viewModel.load()
        advanceUntilIdle()
        viewModel.toggleGenre("drama")
        advanceUntilIdle()

        assertEquals(setOf("drama"), viewModel.filterState.value.selectedGenreIds)
        assertEquals(setOf("drama"), repository.receivedFilters.last().selectedGenreIds)
        assertEquals(2, repository.receivedFilters.size)
    }
}

private class FilterCatalogRepository : CatalogRepository {
    val receivedFilters = mutableListOf<FilterState>()

    override suspend fun categories(session: Session): Result<List<Category>> =
        Result.success(listOf(Category("featured", "Featured")))

    override suspend fun titles(
        session: Session,
        categoryId: String?,
        filter: FilterState,
    ): Result<List<TitleSummary>> {
        receivedFilters += filter
        return Result.success(emptyList())
    }

    override suspend fun filterOptions(session: Session): Result<FilterState> =
        Result.success(
            FilterState(
                availableGenres = listOf("drama" to "Drama"),
            ),
        )

    override suspend fun details(session: Session, titleId: String): Result<TitleDetails> =
        error("Not used")

    override suspend fun continueWatching(session: Session): Result<List<TitleSummary>> =
        Result.success(emptyList())
}

private class FilterSessionStore(
    private val session: Session?,
) : SessionStore {
    override suspend fun get(): Session? = session
    override suspend fun set(session: Session) = Unit
    override suspend fun clear() = Unit
}
