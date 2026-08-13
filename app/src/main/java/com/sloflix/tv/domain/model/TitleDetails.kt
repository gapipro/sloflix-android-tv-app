package com.sloflix.tv.domain.model

enum class MediaKind {
    Movie,
    Show,
    Episode,
}

data class EpisodeSummary(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val episodeIndex: Int,
)

data class TitleDetails(
    val id: String,
    val name: String,
    val description: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val genres: List<String>,
    val resumePositionMs: Long?,
    val duration: String? = null,
    val ratingLabel: String? = null,
    val kind: MediaKind = MediaKind.Movie,
    val seasons: List<Int> = emptyList(),
    val season: Int? = null,
    val episodeIndex: Int? = null,
    val parentId: String? = null,
    val showName: String? = null,
) {
    val isSeriesUi: Boolean
        get() = kind == MediaKind.Show || kind == MediaKind.Episode

    val seriesShowId: String
        get() = when (kind) {
            MediaKind.Episode -> parentId ?: id
            else -> id
        }
}
