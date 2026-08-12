package com.sloflix.tv

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sloflix.tv.di.AppContainer
import com.sloflix.tv.ui.home.HomeViewModel
import com.sloflix.tv.ui.login.LoginViewModel

class SloflixApp : Application() {
    lateinit var container: AppContainer
        private set

    val loginViewModelFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(LoginViewModel::class.java))
                return LoginViewModel(
                    authRepository = container.authRepository,
                    sessionStore = container.sessionStore,
                ) as T
            }
        }
    }

    val homeViewModelFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                return HomeViewModel(
                    catalogRepository = container.catalogRepository,
                    sessionStore = container.sessionStore,
                ) as T
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
