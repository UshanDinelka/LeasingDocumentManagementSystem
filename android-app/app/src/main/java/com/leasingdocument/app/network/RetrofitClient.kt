package com.leasingdocument.app.network

import com.leasingdocument.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // =========================================================
    // BASE URL
    // =========================================================
    //
    // Android Emulator -> Windows PC localhost
    //
    // 10.0.2.2 is the special host address used by the
    // Android Emulator to access services running on the PC.
    //
    // Spring Boot backend:
    // http://localhost:8080
    //
    // Android Emulator:
    // http://10.0.2.2:8080/
    //
    // =========================================================

    private const val BASE_URL =
        "https://192.168.8.101:8443/"


    // =========================================================
    // AUTHORIZATION INTERCEPTOR
    // =========================================================

    private val authInterceptor =
        Interceptor { chain ->

            val originalRequest =
                chain.request()

            val requestBuilder =
                originalRequest
                    .newBuilder()

            AuthSession.token
                ?.takeIf {
                    it.isNotBlank()
                }
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

            redactHeader(
                "Authorization"
            )

            level =
                HttpLoggingInterceptor
                    .Level
                    .BASIC
        }


    // =========================================================
    // OKHTTP CLIENT
    // =========================================================

    private val client:
            OkHttpClient by lazy {

        OkHttpClient
            .Builder()

            .addInterceptor(
                authInterceptor
            )

            .readTimeout(
                240,
                TimeUnit.SECONDS
            )

            .connectTimeout(
                30,
                TimeUnit.SECONDS
            )

            .writeTimeout(
                240,
                TimeUnit.SECONDS
            )

            .callTimeout(
                260,
                TimeUnit.SECONDS
            )

            .apply {

                if (
                    BuildConfig.DEBUG
                ) {

                    addInterceptor(
                        loggingInterceptor
                    )
                }
            }

            .build()
    }


    // =========================================================
    // RETROFIT
    // =========================================================

    val apiService:
            ApiService by lazy {

        Retrofit
            .Builder()

            .baseUrl(
                BASE_URL
            )

            .client(
                client
            )

            .addConverterFactory(
                GsonConverterFactory
                    .create()
            )

            .build()

            .create(
                ApiService::class.java
            )
    }
}