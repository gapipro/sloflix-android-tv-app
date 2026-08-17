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
    /** True when at least one ExoPlayer-compatible media_source exists. */
    val hasExoPlayback: Boolean = true,
    /** Short provider label for ExoPlayer sources, e.g. DoodStream. */
    val exoSourceLabel: String? = null,
    /** StreamP2P embeds resolved to HLS via decrypt for ExoPlayer. */
    val webViewSources: List<WebViewPlaybackSource> = emptyList(),
) {
    /** Series (show) details use the seasons/episodes layout; episodes use movie-like playback UI. */
    val isSeriesUi: Boolean
        get() = kind == MediaKind.Show

    val seriesShowId: String
        get() = when (kind) {
            MediaKind.Episode -> parentId ?: id
            else -> id
        }

    val displayName: String
        get() = when (kind) {
            MediaKind.Episode -> {
                val index = episodeIndex
                if (index != null) "$index. $name" else name
            }
            else -> name
        }
}
