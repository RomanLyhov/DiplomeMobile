package com.example.fitplan.Models.Api

import com.example.fitplan.Models.Nutriments
import com.google.gson.annotations.SerializedName

data class ApiProduct(
    @SerializedName("product_name")
    val productName: String?,

    @SerializedName("brands")
    val brands: String?,

    @SerializedName("nutriments")
    val nutriments: Nutriments?,

    @SerializedName("code")
    val code: String?,

    @SerializedName("image_url")
    val imageUrl: String?
)