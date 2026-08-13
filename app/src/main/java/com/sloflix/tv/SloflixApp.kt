package com.sloflix.tv

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sloflix.tv.di.AppContainer
import com.sloflix.tv.ui.details.DetailsViewModel
import com.sloflix.tv.ui.home.HomeViewModel
import com.sloflix.tv.ui.login.LoginViewModel
import com.sloflix.tv.ui.player.PlayerViewModel

class SloflixApp : Application(), ImageLoaderFactory {
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
                    playbackRepository = container.playbackRepository,
                    sessionStore = container.sessionStore,
                ) as T
            }
        }
    }

    val detailsViewModelFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(DetailsViewModel::class.java))
                return DetailsViewModel(
                    catalogRepository = container.catalogRepository,
                    playbackRepository = container.playbackRepository,
                    sessionStore = container.sessionStore,
                ) as T
            }
        }
    }

    val playerViewModelFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(PlayerViewModel::class.java))
                return PlayerViewModel(
                    playbackRepository = container.playbackRepository,
                    sessionStore = container.sessionStore,
                    languageStore = container.languageStore,
                ) as T
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    // Artwork lives on unauthenticated CDNs, so Coil reuses the credential-free media client.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { container.mediaOkHttpClient }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("poster_cache"))
                .maxSizeBytes(96L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
