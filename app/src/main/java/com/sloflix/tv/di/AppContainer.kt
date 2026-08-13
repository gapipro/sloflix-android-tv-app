package com.sloflix.tv.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sloflix.tv.data.api.AuthInterceptor
import com.sloflix.tv.data.api.CloudflareChallengeInterceptor
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.data.api.UserAgentInterceptor
import com.sloflix.tv.data.net.AndroidNetworkStatus
import com.sloflix.tv.data.playback.DataStoreContinueWatchingStore
import com.sloflix.tv.data.repo.AuthRepositoryImpl
import com.sloflix.tv.data.repo.CatalogRepositoryImpl
import com.sloflix.tv.data.repo.PlaybackRepositoryImpl
import com.sloflix.tv.data.session.DataStoreSessionStore
import com.sloflix.tv.data.settings.DataStoreLanguageStore
import com.sloflix.tv.domain.playback.ContinueWatchingStore
import com.sloflix.tv.domain.repo.AuthRepository
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.SessionStore
import com.sloflix.tv.domain.settings.LanguageStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

private val networkJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

class AppContainer(
    context: Context,
    baseUrl: String = BASE_URL,
) {
    private val sessionProvider = MutableSessionProvider()

    val sessionStore: SessionStore = DataStoreSessionStore(context.applicationContext)
    val languageStore: LanguageStore = DataStoreLanguageStore(context.applicationContext)
    val continueWatchingStore: ContinueWatchingStore =
        DataStoreContinueWatchingStore(context.applicationContext)

    /** Sloflix API only: this is the one client allowed to attach the bearer token and cookies. */
    val apiOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor())
        .addInterceptor(AuthInterceptor(sessionProvider))
        .addInterceptor(CloudflareChallengeInterceptor())
        .build()

    /**
     * Media and image downloads go to third-party CDNs, so they must never carry Sloflix
     * credentials. Sharing the connection pool keeps the extra client cheap.
     */
    val mediaOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(apiOkHttpClient.connectionPool)
        .dispatcher(apiOkHttpClient.dispatcher)
        .addInterceptor(UserAgentInterceptor())
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(apiOkHttpClient)
        .addConverterFactory(
            networkJson.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    private val api = retrofit.create(SloflixApi::class.java)

    val authRepository: AuthRepository = AuthRepositoryImpl(
        api = api,
        sessionProvider = sessionProvider,
        networkStatus = AndroidNetworkStatus(context.applicationContext),
    )
    val catalogRepository: CatalogRepository = CatalogRepositoryImpl(
        api = api,
        sessionProvider = sessionProvider,
        continueWatchingStore = continueWatchingStore,
    )
    val playbackRepository: PlaybackRepository = PlaybackRepositoryImpl(
        api = api,
        sessionProvider = sessionProvider,
        continueWatchingStore = continueWatchingStore,
    )

    companion object {
        const val BASE_URL = "https://api.sloflix.com/v1/"
    }
}
