package com.sloflix.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.sloflix.tv.ui.login.LoginViewModel
import com.sloflix.tv.ui.nav.SloflixNav
import com.sloflix.tv.ui.theme.SloflixTheme

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels {
        (application as SloflixApp).loginViewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SloflixTheme {
                SloflixNav(loginViewModel)
            }
        }
    }
}
