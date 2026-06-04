package com.example.fitplan.Models

data class MealDto(
    val id: Long = 0,
    val userId: Long,

    val productId: Long = 0,

    val productName: String? = null,

    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,

    val quantity: Int,
    val mealType: String?,
    val date: Long = System.currentTimeMillis()
)