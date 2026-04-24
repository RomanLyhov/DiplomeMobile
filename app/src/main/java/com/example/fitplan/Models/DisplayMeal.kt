package com.example.fitplan.ui.models

data class DisplayMeal(
    val mealType: String,
    val productCount: Int,
    val totalCalories: Int,
    val description: String,
    val isExpanded: Boolean = false
)