package com.example.fitplan.Models.Api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ServerApiClient {
    private const val BASE_URL = "http://192.168.0.6:2288/"

    val apiService: ServerApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ServerApiService::class.java)
    }
}
