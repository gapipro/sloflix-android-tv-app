package com.sloflix.tv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sloflix.tv.domain.settings.AppLanguage
import com.sloflix.tv.domain.settings.LanguageStore
import com.sloflix.tv.ui.components.SloflixSplash
import com.sloflix.tv.ui.components.UiState
import com.sloflix.tv.ui.details.DetailsScreen
import com.sloflix.tv.ui.details.DetailsViewModel
import com.sloflix.tv.ui.home.HomeScreen
import com.sloflix.tv.ui.home.HomeViewModel
import com.sloflix.tv.ui.i18n.LocalStrings
import com.sloflix.tv.ui.i18n.stringsFor
import com.sloflix.tv.ui.login.LoginEvent
import com.sloflix.tv.ui.login.LoginScreen
import com.sloflix.tv.ui.login.LoginUiState
import com.sloflix.tv.ui.login.LoginViewModel
import com.sloflix.tv.ui.login.SessionDestination
import com.sloflix.tv.ui.player.PlayerScreen
import com.sloflix.tv.ui.player.PlayerViewModel
import com.sloflix.tv.ui.player.WebViewPlayerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private const val LoginRoute = "login"
private const val HomeRoute = "home"
private const val DetailsRoute = "details/{id}"
private const val PlayerRoute = "player/{id}?startPosition={startPosition}"
private const val WebViewPlayerRoute = "webviewPlayer"
private const val WebViewUrlKey = "webview_url"
private const val StreamP2pEmbedKey = "streamp2p_embed"
private const val MinSplashMs = 1_000L

@Composable
fun SloflixNav(
    loginViewModel: LoginViewModel,
    homeViewModel: HomeViewModel,
    detailsViewModel: DetailsViewModel,
    playerViewModel: PlayerViewModel,
    languageStore: LanguageStore,
    mediaOkHttpClient: OkHttpClient,
    modifier: Modifier = Modifier,
) {
    val state by loginViewModel.uiState.collectAsStateWithLifecycle()
    val language by languageStore.language.collectAsStateWithLifecycle(initialValue = AppLanguage.Default)
    var minSplashElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(loginViewModel) {
        loginViewModel.restoreSession()
    }
    LaunchedEffect(Unit) {
        delay(MinSplashMs)
        minSplashElapsed = true
    }

    CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
        val showSplash = state.destination == SessionDestination.Checking || !minSplashElapsed
        if (showSplash) {
            SplashScreen(modifier)
        } else {
            SloflixNavContent(
                loginViewModel = loginViewModel,
                homeViewModel = homeViewModel,
                detailsViewModel = detailsViewModel,
                playerViewModel = playerViewModel,
                languageStore = languageStore,
                mediaOkHttpClient = mediaOkHttpClient,
                state = state,
                selectedLanguage = language,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun SloflixNavContent(
    loginViewModel: LoginViewModel,
    homeViewModel: HomeViewModel,
    detailsViewModel: DetailsViewModel,
    playerViewModel: PlayerViewModel,
    languageStore: LanguageStore,
    mediaOkHttpClient: OkHttpClient,
    state: LoginUiState,
    selectedLanguage: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val homeFilter by homeViewModel.filterState.collectAsStateWithLifecycle()
    val focusedTitleId by homeViewModel.focusedTitleId.collectAsStateWithLifecycle()
    val detailsState by detailsViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(loginViewModel, navController) {
        loginViewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateHome -> navController.navigate(HomeRoute) {
                    popUpTo(LoginRoute) { inclusive = true }
                    launchSingleTop = true
                }
                LoginEvent.SignedOut -> {
                    homeViewModel.reset()
                    navController.navigate(LoginRoute) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = when (state.destination) {
            SessionDestination.Home -> HomeRoute
            SessionDestination.Login -> LoginRoute
            SessionDestination.Checking -> error("Session check must complete before navigation")
        },
        modifier = modifier,
    ) {
        composable(LoginRoute) {
            LoginScreen(
                state = state,
                onUsernameChanged = loginViewModel::onUsernameChanged,
                onPasswordChanged = loginViewModel::onPasswordChanged,
                onSubmit = loginViewModel::submit,
            )
        }
        composable(HomeRoute) {
            LaunchedEffect(homeViewModel) {
                homeViewModel.load()
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner, homeViewModel) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    homeViewModel.refreshIfLoaded()
                }
            }
            HomeScreen(
                state = homeState,
                filter = homeFilter,
                focusedTitleId = focusedTitleId,
                username = state.username,
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { language ->
                    scope.launch { languageStore.set(language) }
                },
                onRetry = homeViewModel::retry,
                onQueryChanged = homeViewModel::updateQuery,
                onCategorySelected = homeViewModel::selectCategory,
                onGenreToggle = homeViewModel::toggleGenre,
                onYearSelected = homeViewModel::selectYear,
                onTypeSelected = homeViewModel::selectType,
                onSortSelected = homeViewModel::selectSort,
                onClearFilters = homeViewModel::clearFilters,
                onSignOut = loginViewModel::signOut,
                onTitleClick = { title ->
                    homeViewModel.rememberFocusedTitle(title.id)
                    navController.navigate("details/${title.id}")
                },
                onRemoveContinueWatching = { title ->
                    homeViewModel.removeFromContinueWatching(title.id)
                },
            )
        }
        composable(DetailsRoute) { backStackEntry ->
            val titleId = backStackEntry.arguments?.getString("id").orEmpty()
            LaunchedEffect(detailsViewModel, titleId) {
                detailsViewModel.load(titleId)
            }
            val displayState = when (val state = detailsState) {
                is UiState.Ready ->
                    if (state.value.requestId == titleId) state else UiState.Loading
                else -> state
            }
            DetailsScreen(
                state = displayState,
                onRetry = detailsViewModel::retry,
                onSeasonSelected = detailsViewModel::selectSeason,
                onEpisodeClick = { episodeId ->
                    navController.navigate("details/$episodeId")
                },
                onOpenParentShow = { parentId ->
                    navController.navigate("details/$parentId")
                },
                onPlay = { id, startPositionMs ->
                    val route = buildString {
                        append("player/$id")
                        startPositionMs?.let { append("?startPosition=$it") }
                    }
                    navController.navigate(route)
                },
                onPlayWebView = { url ->
                    // Kept for fallback / tests; StreamP2P buttons use onPlayStreamP2P.
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(WebViewUrlKey, url)
                    navController.navigate(WebViewPlayerRoute)
                },
                onPlayStreamP2P = { titleId, embedUrl, startPositionMs ->
                    // Works for movies and episode ids; PlayerViewModel decrypts via StreamP2PClient.
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(StreamP2pEmbedKey, embedUrl)
                    val route = buildString {
                        append("player/$titleId")
                        startPositionMs?.let { append("?startPosition=$it") }
                    }
                    navController.navigate(route)
                },
            )
        }
        composable(WebViewPlayerRoute) {
            val url = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>(WebViewUrlKey)
                .orEmpty()
            if (url.isBlank()) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                WebViewPlayerScreen(
                    url = url,
                    onBack = navController::popBackStack,
                )
            }
        }
        composable(
            route = PlayerRoute,
            arguments = listOf(
                navArgument("startPosition") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val embedUrl = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.remove<String>(StreamP2pEmbedKey)
            PlayerScreen(
                titleId = backStackEntry.arguments?.getString("id").orEmpty(),
                startPositionMs = backStackEntry.arguments?.getLong("startPosition") ?: 0L,
                viewModel = playerViewModel,
                mediaOkHttpClient = mediaOkHttpClient,
                streamP2pEmbedUrl = embedUrl,
                onBack = navController::popBackStack,
            )
        }
    }
}

@Composable
private fun SplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C12)),
        contentAlignment = Alignment.Center,
    ) {
        SloflixSplash()
    }
}
