package com.example.fitplan.Models.Api

import android.util.Log
import com.example.fitplan.DataBase.DatabaseHelper
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

        searchCache[queryLower]?.let { return it }

        for (i in queryLower.length downTo 2) {
            val prefix = queryLower.substring(0, i)
            prefixCache[prefix]?.let { cached ->
                val filtered = cached.filter {
                    it.name.lowercase().contains(queryLower)
                }
                if (filtered.isNotEmpty()) return filtered
            }
        }

        return try {

            val response = withTimeoutOrNull(2000) {
                foodApi.searchProducts(
                    query = query,
                    simple = 1,
                    action = "process",
                    json = 1,
                    pageSize = 15
                )
            } ?: return emptyList()

            val products = convertProducts(response.products)

            if (products.isNotEmpty()) {

                searchCache[queryLower] = products

                for (i in 2..queryLower.length) {
                    val prefix = queryLower.substring(0, i)
                    val filtered = products.filter {
                        it.name.lowercase().contains(prefix)
                    }
                    if (filtered.isNotEmpty()) {
                        prefixCache[prefix] = filtered
                    }
                }

                products.forEach {
                    productCache[it.name.lowercase()] = it
                }

                if (searchCache.size > 50) {
                    searchCache.keys.firstOrNull()?.let { searchCache.remove(it) }
                }

                if (prefixCache.size > 100) {
                    prefixCache.keys.firstOrNull()?.let { prefixCache.remove(it) }
                }
            }

            products

        } catch (e: Exception) {
            Log.e("ApiManager", "searchProducts error", e)
            emptyList()
        }
    }

    private fun convertProducts(apiProducts: List<ApiProduct>): List<Product> {
        return apiProducts.mapNotNull {
            try {
                val name = it.productName?.trim() ?: return@mapNotNull null
                val nutriments = it.nutriments ?: return@mapNotNull null

                Product(
                    id = 0,
                    name = name.take(50),
                    calories = nutriments.energyKcal100g ?: 0f,
                    protein = nutriments.proteins ?: 0f,
                    fat = nutriments.fat ?: 0f,
                    carbs = nutriments.carbohydrates ?: 0f,
                    brand = it.brands ?: "",
                    barcode = it.code ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }.distinctBy { it.name }
    }

    fun getCachedProductByName(name: String): Product? {
        return productCache[name.lowercase()]
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

    // ================= SYNC USERS (FIXED) =================

    suspend fun syncUsers(users: List<UserDto>): Boolean {
        return try {
            val response = serverApi.syncUsers(users)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("ApiManager", "syncUsers error", e)
            false
        }
    }

    suspend fun syncLocalUsersToServer(db: DatabaseHelper) {
        withContext(Dispatchers.IO) {
            try {

                val unsynced = db.getUnsyncedUsers()

                for (user in unsynced) {

                    val dto = UserDto(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        password = user.password,
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

                    val success = addUser(dto)

                    if (success) {
                        db.markUserSynced(user.id)
                    }
                }

            } catch (e: Exception) {
                Log.e("Sync", "syncLocalUsersToServer error", e)
            }
        }
    }

    // ================= WORKOUTS =================

    suspend fun addWorkout(workout: WorkoutDto): Boolean {
        return try {
            val response = serverApi.addWorkout(workout)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("ApiManager", "addWorkout error", e)
            false
        }
    }

    suspend fun getWorkouts(userId: Long): List<WorkoutDto>? {
        return try {
            val response = serverApi.getWorkouts(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApiManager", "getWorkouts error", e)
            null
        }
    }

    // ================= MEALS =================

    suspend fun addMeal(meal: MealDto): Boolean {
        return try {
            val response = serverApi.addMeal(meal)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("ApiManager", "addMeal error", e)
            false
        }
    }

    suspend fun getMeals(userId: Long): List<MealDto>? {
        return try {
            val response = serverApi.getMeals(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApiManager", "getMeals error", e)
            null
        }
    }

    // ================= EXERCISES =================

    suspend fun addExercise(ex: WorkoutExerciseDto): Boolean {
        return try {
            val response = serverApi.addExercise(ex)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.e("ApiManager", "addExercise error", e)
            false
        }
    }

    suspend fun getExercises(userId: Long): List<WorkoutExerciseDto>? {
        return try {
            val response = serverApi.getExercises(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApiManager", "getExercises error", e)
            null
        }
    }

    suspend fun getProducts(): List<ProductDto>? {
        return try {
            val response = serverApi.getProducts()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            Log.e("ApiManager", "getProducts error", e)
            null
        }
    }
}