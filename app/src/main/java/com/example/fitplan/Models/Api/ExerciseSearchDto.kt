package com.example.fitplan.Models.Api

import com.google.gson.annotations.SerializedName

data class ExerciseSearchDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String
)