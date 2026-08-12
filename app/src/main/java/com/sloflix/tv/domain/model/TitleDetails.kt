package com.sloflix.tv.domain.model

data class TitleDetails(
    val id: String,
    val name: String,
    val description: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val genres: List<String>,
    val resumePositionMs: Long?,
)
