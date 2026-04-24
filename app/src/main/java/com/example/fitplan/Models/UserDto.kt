package com.example.fitplan.Models

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("UserID") val id: Long = 0,
    val name: String,
    val email: String,
    val password: String?,
    val age: Int? = null,
    val height: Int? = null,
    val weight: Int? = null,
    val targetWeight: Int? = null,
    val activity: String? = null,
    val goal: String? = null,
    val gender: String? = null,
    val registerDate: String? = null,
    val profileImage: String? = null,
    val dailyCaloriesGoal: Int? = null,
    val dailyProteinGoal: Int? = null,
    val dailyFatGoal: Int? = null,
    val dailyCarbsGoal: Int? = null,
    var updatedAt: String? = null
)
