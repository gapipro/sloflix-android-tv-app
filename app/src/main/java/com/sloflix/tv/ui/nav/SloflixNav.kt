package com.sloflix.tv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.sloflix.tv.ui.components.UiState
import com.sloflix.tv.ui.details.DetailsScreen
import com.sloflix.tv.ui.details.DetailsViewModel
import com.sloflix.tv.ui.home.HomeScreen
import com.sloflix.tv.ui.home.HomeViewModel
import com.sloflix.tv.ui.login.LoginEvent
import com.sloflix.tv.ui.login.LoginScreen
import com.sloflix.tv.ui.login.LoginUiState
import com.sloflix.tv.ui.login.LoginViewModel
import com.sloflix.tv.ui.login.SessionDestination

private const val LoginRoute = "login"
private const val HomeRoute = "home"
private const val DetailsRoute = "details/{id}"
private const val PlayerRoute = "player/{id}?startPosition={startPosition}"

@Composable
fun SloflixNav(
    loginViewModel: LoginViewModel,
    homeViewModel: HomeViewModel,
    detailsViewModel: DetailsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by loginViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(loginViewModel) {
        loginViewModel.restoreSession()
    }

    if (state.destination == SessionDestination.Checking) {
        LoadingScreen(modifier)
    } else {
        SloflixNavContent(
            loginViewModel = loginViewModel,
            homeViewModel = homeViewModel,
            detailsViewModel = detailsViewModel,
            state = state,
            modifier = modifier,
        )
    }
}

@Composable
private fun SloflixNavContent(
    loginViewModel: LoginViewModel,
    homeViewModel: HomeViewModel,
    detailsViewModel: DetailsViewModel,
    state: LoginUiState,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val homeFilter by homeViewModel.filterState.collectAsStateWithLifecycle()
    val detailsState by detailsViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(loginViewModel, navController) {
        loginViewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateHome -> navController.navigate(HomeRoute) {
                    popUpTo(LoginRoute) { inclusive = true }
                    launchSingleTop = true
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
            HomeScreen(
                state = homeState,
                filter = homeFilter,
                onRetry = homeViewModel::retry,
                onQueryChanged = homeViewModel::updateQuery,
                onGenreToggle = homeViewModel::toggleGenre,
                onYearSelected = homeViewModel::selectYear,
                onTypeSelected = homeViewModel::selectType,
                onSortSelected = homeViewModel::selectSort,
                onClearFilters = homeViewModel::clearFilters,
                onTitleClick = { title ->
                    navController.navigate("details/${title.id}")
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
                    if (state.value.title.id == titleId) state else UiState.Loading
                else -> state
            }
            DetailsScreen(
                state = displayState,
                onRetry = detailsViewModel::retry,
                onPlay = { id, startPositionMs ->
                    val route = buildString {
                        append("player/$id")
                        startPositionMs?.let { append("?startPosition=$it") }
                    }
                    navController.navigate(route)
                },
            )
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
            PlayerPlaceholder(
                titleId = backStackEntry.arguments?.getString("id").orEmpty(),
            )
        }
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090C12)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Checking session…",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerPlaceholder(titleId: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090C12)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Player · $titleId",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
    }
}
