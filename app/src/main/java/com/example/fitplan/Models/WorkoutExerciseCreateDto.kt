package com.example.fitplan.Models

import com.google.gson.annotations.SerializedName

data class WorkoutExerciseCreateDto(
    @SerializedName("workoutId")
    val workoutId: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("sets")
    val sets: Int,

    @SerializedName("reps")
    val reps: Int,

    @SerializedName("weight")
    val weight: Float,

    @SerializedName("rest")
    val rest: Int,

    @SerializedName("exerciseId")
val exerciseId: Long? =null
)