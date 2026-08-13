package com.sloflix.tv.data.repo

import com.sloflix.tv.data.api.DetailsDto
import com.sloflix.tv.data.api.DetailsResponse
import com.sloflix.tv.data.api.MediaDto
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.ContinueWatchingEntry
import com.sloflix.tv.domain.model.EpisodeSummary
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.MediaKind
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.playback.ContinueWatchingStore
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.session.Session
import retrofit2.Response

class CatalogRepositoryImpl(
    private val api: SloflixApi,
    private val sessionProvider: MutableSessionProvider,
    private val continueWatchingStore: ContinueWatchingStore,
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
        body.data.map {
            TitleSummary(
                id = it.id.toString(),
                name = it.name,
                posterUrl = it.thumbnailUrl,
                isNew = it.isRecentlyAdded(),
            )
        }
    }

    override suspend fun filterOptions(session: Session): Result<FilterState> =
        categories(session).map { categories ->
            FilterState(
                availableGenres = categories.map { it.id to it.name },
                availableTypes = listOf(
                    1 to "Filmi",
                    2 to "Serije",
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
            dto.toTitleDetails()
        }

    override suspend fun episodes(
        session: Session,
        showId: String,
        season: Int,
    ): Result<List<EpisodeSummary>> = runCatching {
        sessionProvider.update(session)
        val response = api.episodes(showId, season)
        val body = response.body()
        check(response.isSuccessful && body?.status == "success") {
            "Episodes request failed with HTTP ${response.code()}"
        }
        body.data.mapIndexed { index, episode ->
            EpisodeSummary(
                id = episode.id.toString(),
                name = episode.name,
                posterUrl = episode.thumbnailUrl,
                episodeIndex = episode.episodeIndex ?: (index + 1),
            )
        }
    }

    override suspend fun continueWatching(session: Session): Result<List<TitleSummary>> =
        runCatching {
            // No server collection endpoint exists; resume titles are tracked locally when
            // progress is saved, then filtered to the ≥10-minute threshold client-side.
            continueWatchingStore.all()
                .filter { it.positionMs >= ContinueWatchingEntry.MinResumePositionMs }
                .sortedByDescending { it.updatedAtMs }
                .map { it.toTitleSummary() }
        }
}

internal fun DetailsDto.toTitleDetails(): TitleDetails {
    val kind = when {
        mediaType == 2 && season == null -> MediaKind.Show
        mediaType == 2 -> MediaKind.Episode
        else -> MediaKind.Movie
    }
    return TitleDetails(
        id = id.toString(),
        name = name,
        description = description,
        posterUrl = thumbnailUrl,
        backdropUrl = bannerUrl,
        year = year,
        genres = genres.map { it.name },
        resumePositionMs = metadata?.watchTimeSeconds?.times(1_000)?.toLong(),
        duration = length?.takeIf { it.isNotBlank() },
        ratingLabel = rating?.displayLabel(),
        kind = kind,
        seasons = seasons.filter { it > 0 }.ifEmpty { seasons },
        season = season,
        episodeIndex = episodeIndex,
        parentId = parentMediaId?.toString(),
    )
}

internal fun Response<DetailsResponse>.successfulData() =
    body()?.data.takeIf { isSuccessful && body()?.status == "success" }
        ?: error("Details request failed with HTTP ${code()}")

/** Same rule as the web MovieCard NOVO badge: created within the last 7 days. */
internal fun MediaDto.isRecentlyAdded(
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    val createdAt = createdAt?.takeIf { it.isNotBlank() } ?: return false
    val createdMs = runCatching {
        java.time.LocalDateTime.parse(createdAt.replace(' ', 'T'))
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull() ?: return false
    return nowMs - createdMs < NewTitleMaxAgeMs
}

private const val NewTitleMaxAgeMs = 7L * 24 * 60 * 60 * 1_000
