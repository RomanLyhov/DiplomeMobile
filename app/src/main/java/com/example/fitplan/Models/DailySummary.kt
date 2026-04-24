package com.example.fitplan.Models

data class DailySummary(
    val date: String,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalFat: Int,
    val totalCarbs: Int
)