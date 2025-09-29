package com.example.magicwand

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import retrofit2.http.GET
import retrofit2.http.Query


data class DiscoverResp(
    val page:Int,
    val total_pages:Int,
    val results:List<Movie>
)

@Parcelize
data class Movie(
    val id:Int,
    val title:String?,
    val overview:String?,
    val poster_path:String?,
    val release_date:String?,
    val vote_average: Double?
) : Parcelable

interface TmdbApi {
    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-UK"
    ): DiscoverResp

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query:String,
        @Query("page") page:Int = 1,
        @Query("language") language:String = "en-UK",
        @Query("include_adult") includeAdult:Boolean = true
    ): DiscoverResp
}