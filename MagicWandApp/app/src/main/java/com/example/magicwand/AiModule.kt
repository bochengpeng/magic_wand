package com.example.magicwand

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object AiModule {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.AI_API_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    // ✅ Moshi with Kotlin adapter
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val ai: AiApi by lazy {
        val base = BuildConfig.AI_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi)) // ✅ use this
            .build()
            .create(AiApi::class.java)
    }
}

