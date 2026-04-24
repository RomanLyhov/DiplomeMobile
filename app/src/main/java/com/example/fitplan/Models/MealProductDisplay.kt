package com.example.fitplan.Models

data class MealProductDisplay(
    val mealItemId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float
)