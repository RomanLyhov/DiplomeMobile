package com.example.fitplan.Models.Api

import retrofit2.http.GET
import retrofit2.http.Query

interface FoodApi {
    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") query: String,
        @Query("search_simple") simple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): ProductResponse
}