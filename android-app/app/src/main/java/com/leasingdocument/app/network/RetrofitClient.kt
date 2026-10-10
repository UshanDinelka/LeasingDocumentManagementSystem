package com.leasingdocument.app.network

import com.leasingdocument.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /*
     * Physical Android phone -> Windows computer running Spring Boot.
     *
     * Current Windows Wi-Fi IPv4:
     * 10.149.82.89
     *
     * Phone and computer must be connected to the same network.
     */
    private const val BASE_URL = "http://10.149.82.89:8080/"

    // =========================================================
    // AUTHORIZATION INTERCEPTOR
    // =========================================================

    private val authInterceptor = Interceptor { chain ->

        val originalRequest = chain.request()

        val requestBuilder = originalRequest.newBuilder()

        AuthSession.token
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                requestBuilder.header(
                    "Authorization",
                    "Bearer $token"
                )
            }

        chain.proceed(
            requestBuilder.build()
        )
    }

    // =========================================================
    // HTTP LOGGING
    // =========================================================

    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {

            redactHeader("Authorization")

            level = HttpLoggingInterceptor.Level.BASIC
        }

    // =========================================================
    // OKHTTP CLIENT
    // =========================================================

    private val client: OkHttpClient by lazy {

        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .readTimeout(240, TimeUnit.SECONDS)
            .callTimeout(260, TimeUnit.SECONDS)
            .apply {

                if (BuildConfig.DEBUG) {
                    addInterceptor(loggingInterceptor)
                }
            }
            .build()
    }

    // =========================================================
    // RETROFIT
    // =========================================================

    val apiService: ApiService by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(ApiService::class.java)
    }
}