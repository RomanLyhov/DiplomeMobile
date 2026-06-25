package com.example.fitplan.Models

import java.io.Serializable

data class Exercise(
    val id: Long = 0,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Float,
    val rest: Int,
    val description: String = "",
    val initialWeight: Float? = null,
    val isInitial: Boolean = false
): Serializable