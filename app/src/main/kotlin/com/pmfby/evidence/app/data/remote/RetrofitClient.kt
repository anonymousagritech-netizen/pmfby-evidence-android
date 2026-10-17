package com.pmfby.evidence.app.data.remote

import com.pmfby.evidence.app.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the shared Retrofit/OkHttp singleton. Base URL is a BuildConfig field
 * (API_BASE_URL) set from the `apiBaseUrl` Gradle property, defaulting to the
 * Android-emulator alias `http://10.0.2.2:4000/api/` for the host machine's own
 * localhost backend — see README "Backend connection" for pointing this at a LAN IP
 * instead, for a physical device.
 */
object RetrofitClient {
    val moshi: Moshi = Moshi.Builder().build()

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // evidence/voice-note/emergency media uploads
            .addInterceptor(logging)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }
}
