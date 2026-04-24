package com.example.fitplan.Models

import com.google.gson.annotations.SerializedName

data class Nutriments(
    @SerializedName("energy-kcal_100g")
    val energyKcal100g: Float? = null,

    @SerializedName("energy-kcal")
    val energyKcal: Float? = null,

    @SerializedName("proteins_100g")
    val proteins100g: Float? = null,

    @SerializedName("proteins")
    val proteins: Float? = null,

    @SerializedName("fat_100g")
    val fat100g: Float? = null,

    @SerializedName("fat")
    val fat: Float? = null,

    @SerializedName("carbohydrates_100g")
    val carbohydrates100g: Float? = null,

    @SerializedName("carbohydrates")
    val carbohydrates: Float? = null
)