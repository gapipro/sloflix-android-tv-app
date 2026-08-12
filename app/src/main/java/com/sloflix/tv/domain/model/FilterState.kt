package com.sloflix.tv.domain.model

data class FilterState(
    val selectedGenreIds: Set<String> = emptySet(),
    val selectedYear: Int? = null,
    val query: String? = null,
    val availableGenres: List<Pair<String, String>> = emptyList(), // id to label
    val availableYears: List<Int> = emptyList(),
)
