package com.sloflix.tv.domain.model

data class FilterState(
    val selectedGenreIds: Set<String> = emptySet(),
    val selectedYear: Int? = null,
    val query: String? = null,
    val selectedType: Int? = null,
    val sortBy: Int? = null,
    val limit: Int = 100,
    val offset: Int = 0,
    val availableGenres: List<Pair<String, String>> = emptyList(), // id to label
    val availableYears: List<Int> = emptyList(),
    val availableTypes: List<Pair<Int, String>> = emptyList(),
    val availableSorts: List<Pair<Int, String>> = emptyList(),
)
