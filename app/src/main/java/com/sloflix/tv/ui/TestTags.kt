package com.sloflix.tv.ui

/** Stable Compose test tags for instrumented e2e tests. */
object TestTags {
    const val LoginUsername = "login_username"
    const val LoginPassword = "login_password"
    const val LoginSubmit = "login_submit"
    const val LoginError = "login_error"

    const val HomeRoot = "home_root"
    const val HomeSearch = "home_search"
    const val HomeFilters = "home_filters"
    const val HomeProfile = "home_profile"
    const val HomeFilmi = "home_filmi"
    const val HomeSerije = "home_serije"
    const val HomeVse = "home_vse"
    const val HomeContinueWatching = "home_continue_watching"
    const val ContinueWatchingRemoveConfirm = "cw_remove_confirm"
    const val ContinueWatchingRemoveCancel = "cw_remove_cancel"

    const val FilterPanel = "filter_panel"
    const val FilterClose = "filter_close"

    const val ProfileMenu = "profile_menu"
    const val ProfileSettings = "profile_settings"
    const val ProfileSignOut = "profile_sign_out"
    const val ProfileUsername = "profile_username"

    const val SettingsPanel = "settings_panel"
    const val SettingsLanguageSl = "settings_lang_sl"
    const val SettingsLanguageEn = "settings_lang_en"
    const val SettingsClose = "settings_close"

    const val DetailsPlay = "details_play"
    const val DetailsRoot = "details_root"
    const val DetailsSeasonChip = "details_season_chip"
    const val DetailsEpisodes = "details_episodes"

    const val PlayerRoot = "player_root"
    const val PosterPrefix = "poster_"

    fun poster(titleId: String) = "$PosterPrefix$titleId"
}
