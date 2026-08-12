package com.sloflix.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.sloflix.tv.ui.details.DetailsViewModel
import com.sloflix.tv.ui.home.HomeViewModel
import com.sloflix.tv.ui.login.LoginViewModel
import com.sloflix.tv.ui.nav.SloflixNav
import com.sloflix.tv.ui.player.PlayerViewModel
import com.sloflix.tv.ui.theme.SloflixTheme

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels {
        (application as SloflixApp).loginViewModelFactory
    }
    private val homeViewModel: HomeViewModel by viewModels {
        (application as SloflixApp).homeViewModelFactory
    }
    private val detailsViewModel: DetailsViewModel by viewModels {
        (application as SloflixApp).detailsViewModelFactory
    }
    private val playerViewModel: PlayerViewModel by viewModels {
        (application as SloflixApp).playerViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SloflixTheme {
                SloflixNav(
                    loginViewModel = loginViewModel,
                    homeViewModel = homeViewModel,
                    detailsViewModel = detailsViewModel,
                    playerViewModel = playerViewModel,
                    okHttpClient = (application as SloflixApp).container.okHttpClient,
                )
            }
        }
    }
}
