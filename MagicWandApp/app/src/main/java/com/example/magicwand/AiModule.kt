package com.example.magicwand

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object AiModule {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // ✅ define 'client' here
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${BuildConfig.AI_API_KEY}") // ✅ BuildConfig
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(req)
            })
            .build()
    }

    // ✅ use BuildConfig.AI_BASE_URL
    val ai: AiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.AI_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AiApi::class.java)
    }
}
