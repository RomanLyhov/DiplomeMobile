    package com.example.fitplan.Models.Api

    import com.example.fitplan.Models.MealDto
    import com.example.fitplan.Models.UserDto
    import com.example.fitplan.Models.WorkoutDto
    import com.example.fitplan.Models.WorkoutExerciseDto
    import retrofit2.Call
    import retrofit2.Response
    import retrofit2.http.Body
    import retrofit2.http.GET
    import retrofit2.http.POST
    import retrofit2.http.Path
    import retrofit2.http.Query

    interface ApiService {

        @GET("cgi/search.pl")
        fun searchProducts(
            @Query("search_terms") query: String,
            @Query("search_simple") simple: Int = 1,
            @Query("json") json: Int = 1,
            @Query("page_size") pageSize: Int = 10
        ): Call<ProductResponse>

        @POST("users")
        suspend fun addUser(@Body user: UserDto): retrofit2.Response<ApiResponse>

        @POST("workouts")
        suspend fun addWorkout(@Body workout: WorkoutDto): retrofit2.Response<ApiResponse>

        @POST("nutrition")
        suspend fun addMeal(@Body meal: MealDto): retrofit2.Response<ApiResponse>

        @POST("exercises")
        suspend fun addExercise(@Body exercise: WorkoutExerciseDto): Response<ApiResponse>

        @GET("exercises/{userId}")
        suspend fun getExercises(@Path("userId") userId: Long): Response<List<WorkoutExerciseDto>>
    }