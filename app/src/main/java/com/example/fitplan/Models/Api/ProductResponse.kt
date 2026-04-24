package com.example.fitplan.Models.Api

import com.google.gson.annotations.SerializedName

data class ProductResponse(
    @SerializedName("count")
    val count: Int = 0,

    @SerializedName("page")
    val page: Int = 0,

    @SerializedName("page_count")
    val pageCount: Int = 0,

    @SerializedName("page_size")
    val pageSize: Int = 0,

    @SerializedName("products")
    val products: List<ApiProduct> = emptyList()
)