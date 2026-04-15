package com.smartlandmarks.data.api

import com.google.gson.GsonBuilder
import com.smartlandmarks.data.model.LandmarksResponse
import com.smartlandmarks.data.model.LandmarksResponseDeserializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://labs.anontech.info/cse489/exm3/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    // Create Gson with custom deserializer
    private val gson = GsonBuilder()
        .registerTypeAdapter(LandmarksResponse::class.java, LandmarksResponseDeserializer())
        .create()

    val instance: LandmarkApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(LandmarkApiService::class.java)
    }
}
