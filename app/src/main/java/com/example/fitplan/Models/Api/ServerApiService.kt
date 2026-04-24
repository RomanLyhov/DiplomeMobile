package com.example.fitplan.Models.Api

import com.example.fitplan.Models.MealDto
import com.example.fitplan.Models.UserDto
import com.example.fitplan.Models.WorkoutDto
import com.example.fitplan.Models.WorkoutExerciseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ServerApiService {
    @POST("users")
    suspend fun addUser(@Body user: UserDto): Response<ServerResponse>

    @GET("users")
    suspend fun getUsers(): Response<List<UserDto>>
    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") id: Long,
        @Body user: UserDto
    ): Response<ServerResponse>

    @POST("workouts")
    suspend fun addWorkout(@Body workout: WorkoutDto): Response<ServerResponse>
    @POST("/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    @GET("workouts")
    suspend fun getWorkouts(@Query("userId") userId: Long): Response<List<WorkoutDto>>
    @POST("nutrition")
    suspend fun addMeal(@Body meal: MealDto): Response<ServerResponse>
    @POST("/users/sync")
    suspend fun syncUsers(@Body users: List<UserDto>): Response<ApiResponse>

    @GET("meals")
    suspend fun getMeals(@Query("userId") userId: Long): Response<List<MealDto>>

    @GET("products")
    suspend fun getProducts(): Response<List<ProductDto>>

    @POST("exercises")
    suspend fun addExercise(@Body exercise: WorkoutExerciseDto): Response<ServerResponse>

    @GET("exercises")
    suspend fun getExercises(@Query("userId") userId: Long): Response<List<WorkoutExerciseDto>>
}