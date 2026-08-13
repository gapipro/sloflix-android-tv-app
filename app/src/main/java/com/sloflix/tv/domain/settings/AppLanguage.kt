package com.sloflix.tv.domain.settings

enum class AppLanguage(val code: String, val displayName: String) {
    Slovenian("sl", "Slovensko"),
    English("en", "English"),
    ;

    companion object {
        val Default = Slovenian

        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: Default
    }
}
