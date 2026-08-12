package com.sloflix.tv.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.sloflix.tv.data.api.AuthInterceptor
import com.sloflix.tv.data.api.MutableSessionProvider
import com.sloflix.tv.data.api.SloflixApi
import com.sloflix.tv.data.repo.AuthRepositoryImpl
import com.sloflix.tv.data.repo.CatalogRepositoryImpl
import com.sloflix.tv.data.repo.PlaybackRepositoryImpl
import com.sloflix.tv.data.session.DataStoreSessionStore
import com.sloflix.tv.domain.repo.AuthRepository
import com.sloflix.tv.domain.repo.CatalogRepository
import com.sloflix.tv.domain.repo.PlaybackRepository
import com.sloflix.tv.domain.session.SessionStore
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
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(sessionProvider))
        .build()
    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(
            networkJson.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    private val api = retrofit.create(SloflixApi::class.java)

    val authRepository: AuthRepository = AuthRepositoryImpl(api, sessionProvider)
    val catalogRepository: CatalogRepository = CatalogRepositoryImpl(api, sessionProvider)
    val playbackRepository: PlaybackRepository = PlaybackRepositoryImpl(api, sessionProvider)

    companion object {
        const val BASE_URL = "https://api.sloflix.com/v1/"
    }
}
