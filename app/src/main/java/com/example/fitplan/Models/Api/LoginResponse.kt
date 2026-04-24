package com.example.fitplan.Models.Api

data class LoginResponse(
    val success: Boolean,
    val id: Long,
    val role: String? = null,
    val name: String? = null,
    val token: String?
)