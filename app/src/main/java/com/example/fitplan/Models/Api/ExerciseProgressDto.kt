package com.example.fitplan.Models.Api

data class ExerciseProgressDto(
    val userId: Long,
    val exerciseId: Long,
    val workoutId: Long? = null,
    val weight: Float,
    val reps: Int,
    val sets: Int,
    val notes: String? = null
)