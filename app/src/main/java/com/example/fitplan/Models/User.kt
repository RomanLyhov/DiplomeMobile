package com.example.fitplan.Models

data class User(
    val id: Long = -1,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val age: Int? = null,
    val height: Int? = null,
    val weight: Int? = null,
    val targetWeight: Int? = null,
    val activity: String? = null,
    val goal: String? = null,
    val gender: String? = null,
    val registerDate: Long? = null,
    val profileImage: String? = null,
    val dailyCaloriesGoal: Int? = null,
    val dailyProteinGoal: Int? = null,
    val dailyFatGoal: Int? = null,
    val dailyCarbsGoal: Int? = null,
    var isSynced: Boolean = false
)