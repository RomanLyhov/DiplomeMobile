package com.example.fitplan.Models.Api

import com.example.fitplan.Models.WorkoutDto

data class WorkoutResponse(
    val success: Boolean,
    val workout: WorkoutDto?
)