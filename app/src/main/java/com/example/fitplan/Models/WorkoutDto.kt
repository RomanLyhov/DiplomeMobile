package com.example.fitplan.Models

import com.google.gson.annotations.SerializedName

data class WorkoutDto(
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("userId")
    val userId: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("createdAt")
    val createdAt: String? = null
)