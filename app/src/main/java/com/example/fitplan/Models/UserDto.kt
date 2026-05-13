package com.example.fitplan.Models

import com.google.gson.annotations.SerializedName

data class UserDto(

    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("name")
    val name: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("password")
    val password: String?,

    @SerializedName("age")
    val age: Int? = null,

    @SerializedName("height")
    val height: Int? = null,

    @SerializedName("weight")
    val weight: Int? = null,

    @SerializedName("targetWeight")
    val targetWeight: Int? = null,

    @SerializedName("activity")
    val activity: String? = null,

    @SerializedName("goal")
    val goal: String? = null,

    @SerializedName("gender")
    val gender: String? = null,

    @SerializedName("register_date")
    val registerDate: String? = null,

    @SerializedName("profile_image")
    val profileImage: String? = null,

    @SerializedName("dailyCaloriesGoal")
    val dailyCaloriesGoal: Int? = null,

    @SerializedName("dailyProteinGoal")
    val dailyProteinGoal: Int? = null,

    @SerializedName("dailyFatGoal")
    val dailyFatGoal: Int? = null,

    @SerializedName("dailyCarbsGoal")
    val dailyCarbsGoal: Int? = null,

    @SerializedName("updated_at")
    var updatedAt: String? = null
)