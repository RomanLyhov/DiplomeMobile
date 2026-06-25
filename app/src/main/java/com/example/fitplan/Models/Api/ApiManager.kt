package com.example.fitplan.Models.Api

import android.util.Log
import com.example.fitplan.Models.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object ApiManager {

    private val foodApi = ApiClient.api
    private val serverApi = ServerApiClient.apiService

    private val searchCache = ConcurrentHashMap<String, List<Product>>()
    private val productCache = ConcurrentHashMap<String, Product>()
    private val prefixCache = ConcurrentHashMap<String, List<Product>>()

    // ================= PRODUCTS =================

    suspend fun searchProducts(query: String): List<Product> {

        val queryLower = query.trim().lowercase()

        if (queryLower.length < 2) return emptyList()

        // ✅ CACHE CHECK
        searchCache[queryLower]?.let {
            Log.d("FOOD_SEARCH", "CACHE HIT = $queryLower")
            return it
        }

        return try {

            val response = foodApi.searchProducts(
                query = queryLower,
                simple = 1,
                action = "process",
                json = 1,
                pageSize = 15
            )

            val converted = convertProducts(response.products)

            // ✅ SAVE TO CACHE
            searchCache[queryLower] = converted

            converted

        } catch (e: Exception) {
            Log.e("FOOD_SEARCH", "ERROR", e)
            emptyList()
        }
    }
    fun getCachedSearch(query: String): List<Product>? {
        return searchCache[query.trim().lowercase()]
    }
    private fun convertProducts(apiProducts: List<ApiProduct>): List<Product> {

        Log.d("API", "RAW PRODUCTS = ${apiProducts.size}")

        return apiProducts.mapNotNull {

            try {

                val name = it.productName?.trim()

                if (name.isNullOrBlank()) {
                    return@mapNotNull null
                }

                val nutriments = it.nutriments

                Product(
                    id = 0,
                    name = name.take(60),

                    calories = nutriments?.energyKcal100g ?: 0f,
                    protein = nutriments?.proteins ?: 0f,
                    fat = nutriments?.fat ?: 0f,
                    carbs = nutriments?.carbohydrates ?: 0f,

                    brand = it.brands ?: "",
                    barcode = it.code ?: ""
                )

            } catch (e: Exception) {

                Log.e("API", "convert error", e)
                null
            }

        }.distinctBy { it.name.lowercase() }
    }
    fun clearCache() {
        searchCache.clear()
        productCache.clear()
        prefixCache.clear()
        Log.d("ApiManager", "Cache cleared")
    }

    // ================= USERS =================

    suspend fun addUser(user: UserDto): Boolean {
        return try {
            Log.d("API", "➡️ addUser email=${user.email}")

            val response = serverApi.addUser(user)

            Log.d("API", "code=${response.code()}")
            Log.d("API", "body=${response.body()}")
            Log.d("API", "error=${response.errorBody()?.string()}")

            response.isSuccessful && response.body()?.success == true

        } catch (e: Exception) {
            Log.e("API", "addUser crash", e)
            false
        }
    }
    suspend fun getUsers(): List<UserDto>? {
        return try {
            val response = serverApi.getUsers()

            Log.d("API_DEBUG", "RAW RESPONSE: ${response.body()}")

            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("API_DEBUG", "getUsers error", e)
            null
        }
    }

    suspend fun login(email: String, password: String): LoginResponse? {
        return try {
            Log.d("API", "➡️ login email=$email")

            val response = serverApi.login(
                LoginRequest(email, password)
            )

            Log.d("API", "login code=${response.code()}")
            Log.d("API", "login body=${response.body()}")
            Log.d("API", "login error=${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                response.body()
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e("ApiManager", "login error", e)
            null
        }
    }

    private val exerciseCache = ConcurrentHashMap<Long, String>()

    suspend fun getExerciseName(id: Long): String {

        exerciseCache[id]?.let { return it }

        return try {

            val response = serverApi.getExerciseById(id)

            Log.d("EXERCISE_API", "code=${response.code()}")
            Log.d("EXERCISE_API", "body=${response.body()}")
            Log.d("EXERCISE_API", "error=${response.errorBody()?.string()}")

            val name = response.body()?.name ?: "Без имени"

            exerciseCache[id] = name
            name

        } catch (e: Exception) {
            Log.e("EXERCISE_API", "error", e)
            "Без имени"
        }
    }
    suspend fun getWorkoutsFullClient(userId: Long):
            List<Pair<WorkoutDto, List<WorkoutExerciseDto>>> {

        return try {

            val workouts = getWorkouts(userId) ?: emptyList()

            workouts.map { workout ->

                val exercises = try {
                    getExercises(workout.id)
                } catch (e: Exception) {
                    emptyList()
                }

                workout to exercises
            }

        } catch (e: Exception) {
            Log.e("API", "getWorkoutsFullClient error", e)
            emptyList()
        }
    }
    suspend fun getExerciseInitialWeight(userId: Long, exerciseId: Long): Float? {
        return try {
            val response = ServerApiClient.apiService.getExerciseInitialWeight(userId, exerciseId)
            if (response.isSuccessful) {
                response.body()?.initialWeight
            } else null
        } catch (e: Exception) {
            Log.e("ApiManager", "getExerciseInitialWeight error", e)
            null
        }
    }

    suspend fun saveExerciseProgress(dto: ExerciseProgressDto): Boolean {
        return try {
            val response = ServerApiClient.apiService.saveExerciseProgress(dto)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("ApiManager", "saveExerciseProgress error", e)
            false
        }
    }


    // В ApiManager.kt исправьте updateUser:
    suspend fun updateUser(userId: Long, user: UserDto): Boolean {
        return try {

            Log.d("API", "=== UPDATE USER ===")
            Log.d("API", "userId=$userId")

            // DTO -> User
            val tempUser = User(
                id = user.id,
                name = user.name,
                email = user.email,
                password = user.password ?: "",
                age = user.age,
                height = user.height,
                weight = user.weight,
                targetWeight = user.targetWeight,
                activity = user.activity,
                goal = user.goal,
                gender = user.gender,
                dailyCaloriesGoal = user.dailyCaloriesGoal,
                dailyProteinGoal = user.dailyProteinGoal,
                dailyFatGoal = user.dailyFatGoal,
                dailyCarbsGoal = user.dailyCarbsGoal
            )

            // ================= РАСЧЕТ МАКРОСОВ =================

            val macros = com.example.fitplan.Utils.MacroCalculator
                .calculateDailyGoals(tempUser)

            Log.d("API", "Macros=$macros")

            // ================= СОЗДАЕМ ОБНОВЛЕННОГО USER =================

            val updatedUser = user.copy(
                dailyCaloriesGoal = macros?.calories,
                dailyProteinGoal = macros?.protein,
                dailyFatGoal = macros?.fat,
                dailyCarbsGoal = macros?.carbs
            )

            Log.d("API", "dailyCaloriesGoal=${updatedUser.dailyCaloriesGoal}")
            Log.d("API", "dailyProteinGoal=${updatedUser.dailyProteinGoal}")
            Log.d("API", "dailyFatGoal=${updatedUser.dailyFatGoal}")
            Log.d("API", "dailyCarbsGoal=${updatedUser.dailyCarbsGoal}")

            // ================= ОТПРАВКА =================

            val response = serverApi.updateUser(userId, updatedUser)

            Log.d("API", "response code=${response.code()}")
            Log.d("API", "response body=${response.body()}")

            response.isSuccessful &&
                    response.body()?.success == true

        } catch (e: Exception) {

            Log.e("API", "updateUser crash", e)
            false

        }
    }
    suspend fun getWorkouts(userId: Long): List<WorkoutDto>? {
        return try {
            val response = ServerApiClient.apiService.getWorkouts(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addWorkout(workout: WorkoutDto): Long? {
        return try {

            val response = serverApi.addWorkout(workout)

            Log.d("API_WORKOUT", "code=${response.code()}")
            Log.d("API_WORKOUT", "body=${response.body()}")
            Log.d("API_WORKOUT", "error=${response.errorBody()?.string()}")

            if (response.isSuccessful) {

                val body = response.body()

                // 🔥 ВАЖНО: безопасное получение id
                val id = body?.id

                if (id != null && id > 0) {
                    id
                } else {
                    Log.e("API_WORKOUT", "Invalid workout id from server")
                    null
                }

            } else null

        } catch (e: Exception) {
            Log.e("ApiManager", "addWorkout error", e)
            null
        }
    }

    suspend fun addMeal(meal: MealDto): Boolean {
        return try {
            val response = serverApi.addMeal(meal)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("ApiManager", "addMeal error", e)
            false
        }
    }

    suspend fun getMeals(userId: Long, start: Long, end: Long): List<MealDto> {
        return try {

            val response = serverApi.getMeals(userId, start, end)

            Log.d("API_MEALS", "code=${response.code()}")
            Log.d("API_MEALS", "body=${response.body()}")

            response.body()?.forEach {
                Log.d(
                    "API_MEALS",
                    "meal: id=${it.id}, type=${it.mealType}, date=${it.date}, userId=${it.userId}"
                )
            }

            if (response.isSuccessful) {
                response.body()?.map {
                    it.copy(
                        mealType = it.mealType ?: "Другое"
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }

        } catch (e: Exception) {
            Log.e("ApiManager", "getMeals error", e)
            emptyList()
        }
    }

    suspend fun getExercises(workoutId: Long): List<WorkoutExerciseDto> {
        return try {
            val response = serverApi.getExercises(workoutId)

            // 🔥 ДОБАВЬТЕ ПОДРОБНЫЙ ЛОГ
            Log.d("EXERCISES_API", "Response code: ${response.code()}")
            Log.d("EXERCISES_API", "Response body: ${response.body()}")
            Log.d("EXERCISES_API", "Response error: ${response.errorBody()?.string()}")

            if (response.isSuccessful) {
                val exercises = response.body() ?: emptyList()
                Log.d("EXERCISES_API", "Parsed exercises count: ${exercises.size}")
                exercises.forEach { ex ->
                    Log.d("EXERCISES_API", "Exercise: name=${ex.name}, sets=${ex.sets}, reps=${ex.reps}")
                }
                exercises
            } else {
                Log.e("EXERCISES_API", "Response not successful: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ApiManager", "getExercises error", e)
            emptyList()
        }
    }

    suspend fun copyWorkout(userId: Long, workoutId: Long): Boolean {
        return try {
            Log.d("COPY_WORKOUT", "Copying workoutId=$workoutId for userId=$userId")

            val response = ServerApiClient.apiService.copyWorkout(
                CopyWorkoutDto(userId = userId, workoutId = workoutId)
            )

            Log.d("COPY_WORKOUT", "code=${response.code()}")
            Log.d("COPY_WORKOUT", "body=${response.body()}")
            Log.d("COPY_WORKOUT", "error=${response.errorBody()?.string()}")

            response.isSuccessful && response.body()?.success == true

        } catch (e: Exception) {
            Log.e("COPY_WORKOUT", "copyWorkout error", e)
            false
        }
    }
    suspend fun getRecommendedWorkouts(userId: Long): List<WorkoutDto> {
        return try {
            val response = ServerApiClient.apiService.getRecommendedWorkouts(userId)
            if (response.isSuccessful) response.body() ?: emptyList()
            else emptyList()
        } catch (e: Exception) {
            Log.e("ApiManager", "getRecommendedWorkouts error", e)
            emptyList()
        }
    }

    suspend fun createExerciseWithWorkout(dto: WorkoutExerciseCreateDto): Boolean {
        return try {
            val response = serverApi.createExerciseWithWorkout(dto)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("ApiManager", "createExerciseWithWorkout error", e)
            false
        }
    }

}