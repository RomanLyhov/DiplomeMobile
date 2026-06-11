package com.example.fitplan.Models.Api

import com.example.fitplan.Models.WorkoutExerciseDto

data class WorkoutFullDto(
    val id: Long,
    val userId: Long,
    val name: String,
    val createdAt: String,
    val exercises: List<WorkoutExerciseDto>
)