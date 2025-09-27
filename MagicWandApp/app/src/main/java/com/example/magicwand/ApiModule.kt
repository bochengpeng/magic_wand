package com.example.magicwand

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiModule {
    private val client = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.TMDB_ACCESS_TOKEN}")
                .addHeader("accept", "application/json")
                .build()
            chain.proceed(req)
        })
        .build()

    // ✅ Create Moshi with the Kotlin adapter
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val tmdb: TmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/") // use v3 base URL
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApi::class.java)
    }
}