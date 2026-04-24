package com.example.fitplan.Models

data class Meal(
    val id: Long,
    val userId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int = 0,
    val calories: Int = 0,
    val protein: Int = 0,
    val fat: Int = 0,
    val carbs: Int = 0,
    val date: Long,
    val mealType: String?
)
