package com.example.fitplan.Models.Api

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.*
import kotlinx.coroutines.*

object SyncManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val FLOW_TAG = "LOGIN_FLOW"

    private fun logFlow(message: String) {
        Log.d(FLOW_TAG, message)
    }

    fun startSync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            syncAll(context.applicationContext)
        }
    }

    suspend fun syncAll(context: Context) {

        logFlow("================ START SYNC ================")

        if (!isNetworkAvailable(context)) {
            logFlow("❌ NO INTERNET")
            return
        }

        logFlow("🌐 Internet OK")

        val db = DatabaseHelper(context)
        val prefs = context.getSharedPreferences("session", MODE_PRIVATE)
        val userId = prefs.getLong("user_id", -1L)

        logFlow("👤 userId=$userId")

        if (userId == -1L) {
            logFlow("⚠️ SESSION MISSING (user_id = -1)")
        }

        try {

            // ================= PUSH =================
            logFlow("⬆️ PUSH LOCAL CHANGES START")
            pushLocalChangesToServer(db)
            logFlow("⬆️ PUSH LOCAL CHANGES END")

            // ================= USERS =================
            logFlow("⬇️ USERS DOWNLOAD START")

            val users = ApiManager.getUsers()
            logFlow("📦 USERS FROM SERVER = ${users?.size}")

            users?.forEach {
                logFlow("👤 USER ${it.email}")
                db.insertUserFromServer(it)
            }

            // ================= WORKOUTS =================
            logFlow("⬇️ WORKOUTS DOWNLOAD START")

            val workouts = ApiManager.getWorkouts(userId)
            logFlow("📦 WORKOUTS = ${workouts?.size}")

            workouts?.forEach { workout ->
                logFlow("🏋️ workout=${workout.name}")

                val exists = db.getWorkoutByServerId(workout.id)

                if (exists == null) {
                    logFlow("➕ INSERT workout")
                    db.insertWorkoutFromServer(workout.userId, workout.name)
                } else {
                    logFlow("🔄 UPDATE workout")
                    db.updateWorkoutFromServer(workout)
                }
            }

            // ================= MEALS =================
            logFlow("⬇️ MEALS DOWNLOAD START")

            val meals = ApiManager.getMeals(userId)
            logFlow("📦 MEALS = ${meals?.size}")

            meals?.forEach {
                logFlow("🍽 meal=${it.mealType}")
                db.insertMealFromServer(it)
            }

            // ================= EXERCISES =================
            logFlow("⬇️ EXERCISES DOWNLOAD START")

            val exercises = ApiManager.getExercises(userId)
            logFlow("📦 EXERCISES = ${exercises?.size}")

            exercises?.forEach {
                logFlow("💪 exercise sync")
                db.insertExerciseFromServer(it)
            }

        } catch (e: Exception) {
            logFlow("❌ GLOBAL SYNC ERROR: ${e.message}")
            Log.e(FLOW_TAG, "SYNC ERROR", e)
        }

        logFlow("================ END SYNC ================")
    }

    private suspend fun pushLocalChangesToServer(db: DatabaseHelper) {

        try {
            logFlow("⬆️ PUSH USERS START")

            val users = db.getUnsyncedUsers()
            logFlow("📦 unsynced USERS = ${users.size}")

            users.forEach { user ->

                logFlow("➡️ PUSH USER ${user.email}")

                val dto = UserDto(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    password = user.password,
                    age = user.age,
                    height = user.height,
                    weight = user.weight,
                    gender = user.gender,
                    goal = user.goal,
                    activity = user.activity,
                    targetWeight = user.targetWeight,
                    dailyCaloriesGoal = user.dailyCaloriesGoal,
                    dailyProteinGoal = user.dailyProteinGoal,
                    dailyFatGoal = user.dailyFatGoal,
                    dailyCarbsGoal = user.dailyCarbsGoal
                )

                val success = if (user.id > 0) {
                    ApiManager.updateUser(dto)
                } else {
                    ApiManager.addUser(dto)
                }

                logFlow("📡 USER RESULT ${user.email} success=$success")

                if (success) {
                    db.markUserSynced(user.id)
                    logFlow("✅ USER SYNCED ${user.email}")
                } else {
                    logFlow("❌ USER FAILED ${user.email}")
                }
            }

        } catch (e: Exception) {
            logFlow("❌ USERS PUSH ERROR")
        }

        // ================= WORKOUTS =================
        try {
            logFlow("⬆️ PUSH WORKOUTS START")

            val workouts = db.getUnsyncedWorkouts()
            logFlow("📦 unsynced WORKOUTS = ${workouts.size}")

            workouts.forEach { workout ->

                logFlow("➡️ PUSH WORKOUT ${workout.name}")

                val dto = WorkoutDto(
                    id = 0,
                    userId = workout.userId,
                    name = workout.name
                )

                val success = ApiManager.addWorkout(dto)

                logFlow("📡 WORKOUT RESULT=$success")

                if (success) {
                    db.markWorkoutSynced(workout.id)
                    logFlow("✅ WORKOUT SYNCED")
                }
            }

        } catch (e: Exception) {
            logFlow("❌ WORKOUT PUSH ERROR")
        }

        // ================= MEALS =================
        try {
            logFlow("⬆️ PUSH MEALS START")

            val meals = db.getUnsyncedMeals()
            logFlow("📦 unsynced MEALS = ${meals.size}")

            meals.forEach { meal ->

                logFlow("➡️ PUSH MEAL ${meal.mealType}")

                val dto = MealDto(
                    userId = meal.userId,
                    productId = meal.productId,
                    quantity = meal.quantity,
                    calories = meal.calories,
                    protein = meal.protein,
                    fat = meal.fat,
                    carbs = meal.carbs,
                    mealType = meal.mealType
                )

                val success = ApiManager.addMeal(dto)

                logFlow("📡 MEAL RESULT=$success")

                if (success) {
                    db.markMealSynced(meal.id)
                    logFlow("✅ MEAL SYNCED")
                }
            }

        } catch (e: Exception) {
            logFlow("❌ MEAL PUSH ERROR")
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)

            caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
        } else {
            val net = cm.activeNetworkInfo
            net != null && net.isConnected
        }

        logFlow("🌐 NETWORK=$result")
        return result
    }
}