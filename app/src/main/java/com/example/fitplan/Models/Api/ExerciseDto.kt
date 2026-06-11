package com.example.fitplan.Models.Api


import com.google.gson.annotations.SerializedName

data class ExerciseDto(

    @SerializedName("id")
    val id: Long,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("muscleGroup")
    val muscleGroup: String? = null,

    @SerializedName("difficulty")
    val difficulty: String? = null,

    @SerializedName("instruction")
    val instruction: String? = null,

    @SerializedName("videoUrl")
    val videoUrl: String? = null
)
