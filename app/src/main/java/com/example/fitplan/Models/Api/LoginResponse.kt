package com.example.fitplan.Models.Api

data class LoginResponse(
    val success: Boolean,
    val id: Long,
    val role: String? = null,
    val name: String? = null,
    val token: String? = null,

    val targetWeight: Int? = null,
    val dailyCaloriesGoal: Int? = null,
    val dailyProteinGoal: Int? = null,
    val dailyFatGoal: Int? = null,
    val dailyCarbsGoal: Int? = null
)