package com.example.fitplan.Models.Api

data class ServerResponse(
    val success: Boolean,
    val message: String? = null,
    val id: Long? = null
)
