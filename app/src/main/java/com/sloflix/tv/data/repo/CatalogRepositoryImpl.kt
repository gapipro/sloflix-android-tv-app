package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.DetailsResponse
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.session.Session
import retrofit2.Response

class CatalogRepositoryImpl(
    private val api: SloflixApi,
    private val sessionProvider: MutableSessionProvider,
) : CatalogRepository {
    override suspend fun categories(session: Session): Result<List<Category>> = runCatching {
        val response = api.genres()
        val body = response.body()
        check(response.isSuccessful && body?.status == "success") {
            "Genres request failed with HTTP ${response.code()}"
        }
        body.data.map { Category(it.id.toString(), it.name) }
    }

    override suspend fun titles(
        session: Session,
        categoryId: String?,
        filter: FilterState,
    ): Result<List<TitleSummary>> = runCatching {
        sessionProvider.update(session)
        val genreIds = buildSet {
            addAll(filter.selectedGenreIds)
            categoryId?.let(::add)
        }
        val query = filter.query.orEmpty()
        val response = api.media(
            sortBy = filter.sortBy ?: if (query.isNotBlank()) 7 else 1,
            genres = genreIds.joinToString(","),
            type = filter.selectedType,
            query = query,
            limit = filter.limit,
            offset = filter.offset,
        )
        val body = response.body()
        check(response.isSuccessful && body?.status == "success") {
            "Media request failed with HTTP ${response.code()}"
        }
        body.data.map { TitleSummary(it.id.toString(), it.name, it.thumbnailUrl) }
    }

    override suspend fun filterOptions(session: Session): Result<FilterState> =
        categories(session).map { categories ->
            FilterState(
                availableGenres = categories.map { it.id to it.name },
                availableTypes = listOf(
                    1 to "Films",
                    2 to "Series",
                ),
                availableSorts = listOf(
                    1 to "Newest added",
                    2 to "Oldest added",
                    3 to "Highest rating",
                    4 to "Year descending",
                    5 to "Year ascending",
                    6 to "Most watched",
                    7 to "Relevance",
                ),
            )
        }

    override suspend fun details(session: Session, titleId: String): Result<TitleDetails> =
        runCatching {
            sessionProvider.update(session)
            val dto = api.details(titleId, dontCountView = true).successfulData()
            TitleDetails(
                id = dto.id.toString(),
                name = dto.name,
                description = dto.description,
                posterUrl = dto.thumbnailUrl,
                backdropUrl = dto.bannerUrl,
                year = dto.year,
                genres = dto.genres.map { it.name },
                resumePositionMs = dto.metadata?.watchTimeSeconds?.times(1_000)?.toLong(),
            )
        }

    override suspend fun continueWatching(session: Session): Result<List<TitleSummary>> =
        Result.success(emptyList())
}

internal fun Response<DetailsResponse>.successfulData() =
    body()?.data.takeIf { isSuccessful && body()?.status == "success" }
        ?: error("Details request failed with HTTP ${code()}")
