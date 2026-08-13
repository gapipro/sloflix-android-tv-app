package com.sloflix.tv.domain.model

/** Locally tracked in-progress title used to build the home resume row. */
data class ContinueWatchingEntry(
    val titleId: String,
    val name: String,
    val posterUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
) {
    val progressFraction: Float?
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    fun toTitleSummary(): TitleSummary = TitleSummary(
        id = titleId,
        name = name,
        posterUrl = posterUrl,
        progressFraction = progressFraction,
    )

    companion object {
        /** Titles below this watch position are omitted from the resume row. */
        const val MinResumePositionMs: Long = 10L * 60L * 1_000L
    }
}
