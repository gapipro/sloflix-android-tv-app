package com.sloflix.tv.domain.model

data class TitleSummary(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val progressFraction: Float? = null,
)
