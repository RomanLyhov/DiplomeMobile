package com.example.fitplan.Models

data class Product(
    val id: Long = 0,
    val name: String = "",
    val calories: Float = 0f,
    val protein: Float = 0f,
    val fat: Float = 0f,
    val carbs: Float = 0f,
    val brand: String = "",
    val barcode: String = ""
) {
    constructor(
        id: Long,
        name: String,
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float
    ) : this(id, name, calories, protein, fat, carbs, "", "")
}