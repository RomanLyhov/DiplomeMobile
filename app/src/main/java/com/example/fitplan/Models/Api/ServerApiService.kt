package com.example.fitplan.Models.Api

import com.example.fitplan.Models.MealDto
import com.example.fitplan.Models.UserDto
import com.example.fitplan.Models.WorkoutDto
import com.example.fitplan.Models.WorkoutExerciseDto
import retrofit2.Response
import com.example.fitplan.Models.WorkoutExerciseCreateDto
import retrofit2.http.Body
import retrofit2.http.DELETE
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

    @PUT("workouts/{id}")
    suspend fun updateWorkout(
        @Path("id") id: Long,
        @Body workout: WorkoutDto
    ): Response<ServerResponse>

    @POST("workouts")
    suspend fun addWorkout(@Body workout: WorkoutDto): Response<WorkoutResponse>

    @POST("/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("workouts/{userId}")
    suspend fun getWorkouts(
        @Path("userId") userId: Long
    ): Response<List<WorkoutDto>>

    @POST("meals")
    suspend fun addMeal(@Body meal: MealDto): Response<ServerResponse>

    @POST("/users/sync")
    suspend fun syncUsers(@Body users: List<UserDto>): Response<ApiResponse>

    @GET("exercises/{id}")
    suspend fun getExerciseById(@Path("id") id: Long): Response<ExerciseDto>

    @GET("meals")
    suspend fun getMeals(
        @Query("userId") userId: Long,
        @Query("start") start: Long,
        @Query("end") end: Long
    ): Response<List<MealDto>>

    @GET("products")
    suspend fun getProducts(): Response<List<ProductDto>>

    // Единственный метод для создания упражнения
    @POST("exercises")
    suspend fun createExerciseWithWorkout(
        @Body dto: WorkoutExerciseCreateDto
    ): Response<ServerResponse>

    @GET("workout-exercises/{workoutId}")
    suspend fun getExercises(
        @Path("workoutId") workoutId: Long
    ): Response<List<WorkoutExerciseDto>>

    @POST("/workout-exercises")
    suspend fun addWorkoutExercise(
        @Body dto: com.example.fitplan.Models.WorkoutExerciseCreateDto
    ): Response<SimpleResponse>
    @GET("exercises/search")
    suspend fun searchExercises(
        @Query("q") query: String
    ): Response<List<ExerciseDto>>

    @DELETE("workouts/{id}")
    suspend fun deleteWorkout(
        @Path("id") id: Long
    ): Response<ServerResponse>
    @DELETE("workouts/{id}/exercises")
    suspend fun deleteWorkoutExercises(
        @Path("id") workoutId: Long
    ): Response<ServerResponse>

    @POST("calendar")
    suspend fun addToCalendar(
        @Body dto: CalendarCreateDto
    ): Response<ServerResponse>

    @POST("workout-history")
    suspend fun saveWorkoutHistory(@Body dto: WorkoutHistoryDto): Response<ServerResponse>

    @GET("workout-history/{userId}")
    suspend fun getWorkoutHistory(
        @Path("userId") userId: Long,
        @Query("startDate") startDate: Long? = null,
        @Query("endDate") endDate: Long? = null
    ): Response<List<WorkoutHistoryDto>>
}