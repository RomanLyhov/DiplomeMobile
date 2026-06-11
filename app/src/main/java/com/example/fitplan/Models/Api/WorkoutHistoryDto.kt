package com.example.fitplan.Models.Api

data class WorkoutHistoryDto(
    val id: Long = 0,
    val userId: Long,
    val workoutId: Long,
    val workoutName: String,
    val completedAt: Long,
    val totalExercises: Int,
    val completedExercises: Int,
    val isFullyDone: Boolean
)