package com.sloflix.tv.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SloflixApi {
    @POST("user/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("user/preferences/")
    suspend fun preferences(): Response<StatusResponse>

    @GET("genre")
    suspend fun genres(): Response<GenreResponse>

    @GET("media")
    suspend fun media(
        @Query("sortBy") sortBy: Int,
        @Query("genres") genres: String,
        @Query("type") type: Int? = null,
        @Query("query") query: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): Response<MediaResponse>

    @GET("media/single/{titleId}")
    suspend fun details(
        @Path("titleId") titleId: String,
        @Query("dont_count_view") dontCountView: Boolean? = null,
    ): Response<DetailsResponse>

    @POST("media/{titleId}/player/metadata")
    suspend fun saveProgress(
        @Path("titleId") titleId: String,
        @Body request: ProgressRequest,
    ): Response<StatusResponse>
}

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(
    val code: Int,
    val status: String,
    val message: String? = null,
    val metadata: LoginMetadata? = null,
)

@Serializable
data class LoginMetadata(@SerialName("access_token") val accessToken: String)

@Serializable
data class StatusResponse(
    val code: Int,
    val status: String,
)

@Serializable
data class GenreResponse(
    val code: Int,
    val status: String,
    val data: List<GenreDto> = emptyList(),
)

@Serializable
data class GenreDto(
    @SerialName("genre_id") val id: Int,
    @SerialName("genre_name") val name: String,
)

@Serializable
data class MediaResponse(
    val code: Int,
    val status: String,
    val data: List<MediaDto> = emptyList(),
)

@Serializable
data class MediaDto(
    @SerialName("media_id") val id: Int,
    @SerialName("media_name") val name: String,
    @SerialName("media_thumbnail_url") val thumbnailUrl: String? = null,
)

@Serializable
data class DetailsResponse(
    val code: Int,
    val status: String,
    val data: DetailsDto? = null,
)

@Serializable
data class DetailsDto(
    @SerialName("media_id") val id: Int,
    @SerialName("media_name") val name: String,
    @SerialName("media_description") val description: String = "",
    @SerialName("media_thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("media_banner_url") val bannerUrl: String? = null,
    @SerialName("media_year") val year: Int? = null,
    @SerialName("media_genres") val genres: List<GenreDto> = emptyList(),
    @SerialName("media_sources") val sources: List<MediaSourceDto> = emptyList(),
    val metadata: PlaybackMetadataDto? = null,
)

@Serializable
data class MediaSourceDto(
    @SerialName("media_source") val url: String,
    @SerialName("media_source_name") val name: String? = null,
    @SerialName("subtitle_location") val subtitleLocation: String? = null,
)

@Serializable
data class PlaybackMetadataDto(
    @SerialName("watch_time") val watchTimeSeconds: Double? = null,
)

@Serializable
data class ProgressRequest(
    @SerialName("watch_time") val watchTimeSeconds: Double,
)
