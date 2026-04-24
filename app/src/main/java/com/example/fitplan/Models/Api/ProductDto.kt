package com.example.fitplan.Models.Api

data class ProductDto(
    val id: Long,
    val name: String,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float
)
