package com.sloflix.tv.domain.repo

import com.sloflix.tv.domain.model.Category
import com.sloflix.tv.domain.model.EpisodeSummary
import com.sloflix.tv.domain.model.FilterState
import com.sloflix.tv.domain.model.TitleDetails
import com.sloflix.tv.domain.model.TitleSummary
import com.sloflix.tv.domain.session.Session

interface CatalogRepository {
    suspend fun categories(session: Session): Result<List<Category>>
    suspend fun titles(
        session: Session,
        categoryId: String?,
        filter: FilterState,
    ): Result<List<TitleSummary>>
    suspend fun filterOptions(session: Session): Result<FilterState>
    suspend fun details(session: Session, titleId: String): Result<TitleDetails>
    suspend fun episodes(
        session: Session,
        showId: String,
        season: Int,
    ): Result<List<EpisodeSummary>>
    suspend fun continueWatching(session: Session): Result<List<TitleSummary>>
}
