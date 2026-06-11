// CalendarCreateDto.kt
package com.example.fitplan.Models.Api

import com.google.gson.annotations.SerializedName

data class CalendarCreateDto(
    @SerializedName("userId") val userId: Long,
    @SerializedName("workoutId") val workoutId: Long,
    @SerializedName("scheduledDate") val scheduledDate: Long
)