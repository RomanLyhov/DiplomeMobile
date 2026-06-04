package com.example.fitplan.Models

data class Workout(
    val id: Long,
    val serverId: Long? = null,
    val userId: Long,
    val name: String
)
