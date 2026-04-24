package com.example.fitplan.Models

data class WorkoutExerciseDto(
    val workoutId: Long,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Int,
    val rest: Int
)
