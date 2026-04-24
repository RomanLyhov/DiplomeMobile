package com.example.fitplan.Models

data class MealSummary(
    val mealType: String,
    val productCount: Int,
    val totalQuantity: Int,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalFat: Int,
    val totalCarbs: Int
)