package com.example.fitplan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.User
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseHelperTest {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        dbHelper = DatabaseHelper(context)

        val db = dbHelper.writableDatabase
        db.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS products")
        db.execSQL("DROP TABLE IF EXISTS nutrition_log")
        db.execSQL("DROP TABLE IF EXISTS workouts")
        db.execSQL("DROP TABLE IF EXISTS exercises")
        db.execSQL("DROP TABLE IF EXISTS workout_exercises")
        db.execSQL("DROP TABLE IF EXISTS nutrition_archive")
        dbHelper.onCreate(db)
    }

    @After
    fun tearDown() {
        dbHelper.close()
    }

    @Test
    fun testAddAndGetUser() {
        val testUser = User(
            id = 0L,
            name = "Тестовый пользователь",
            email = "test@example.com",
            password = "password123",
            age = 25,
            height = 180,
            weight = 75,
            targetWeight = 70,
            activity = "MODERATE",
            goal = "WEIGHT_LOSS",
            gender = "MALE",
            dailyCaloriesGoal = 2000,
            dailyProteinGoal = 120,
            dailyFatGoal = 60,
            dailyCarbsGoal = 250
        )
        val userId = dbHelper.addUser(testUser)
        assertTrue("ID пользователя должен быть больше 0", userId > 0)
        val retrievedUser = dbHelper.getUserById(userId)
        assertNotNull("Пользователь должен быть найден", retrievedUser)
        assertEquals("Имена должны совпадать", "Тестовый пользователь", retrievedUser?.name)
        assertEquals("Email должен совпадать", "test@example.com", retrievedUser?.email)
        assertEquals("Возраст должен совпадать", 25, retrievedUser?.age)
        assertEquals("Цель по калориям должна совпадать", 2000, retrievedUser?.dailyCaloriesGoal)
    }

    @Test
    fun testUserAuthentication() {
        val email = "auth@test.com"
        val password = "securePass123"
        val authUser = User(
            id = 0L,
            name = "Аутентификация",
            email = email,
            password = password,
            age = 30
        )
        val userId = dbHelper.addUser(authUser)
        assertTrue("Пользователь должен быть добавлен", userId > 0)
        val foundUser = dbHelper.getUserByEmailAndPassword(email, password)
        assertNotNull("Пользователь должен быть найден по email и паролю", foundUser)
        assertEquals("Email должен совпадать", email, foundUser?.email)
        assertEquals("Имя должно совпадать", "Аутентификация", foundUser?.name)
    }

    @Test
    fun testUpdateUser() {
        val originalUser = User(
            id = 0L,
            name = "Оригинал",
            email = "original@test.com",
            password = "pass",
            weight = 80,
            dailyCaloriesGoal = 1800
        )
        val userId = dbHelper.addUser(originalUser)
        val retrievedUser = dbHelper.getUserById(userId)
        assertNotNull("Пользователь должен существовать", retrievedUser)
        val updatedUser = retrievedUser!!.copy(
            name = "Обновленный",
            weight = 75,
            dailyCaloriesGoal = 2000
        )
        dbHelper.updateUser(updatedUser)
        val afterUpdate = dbHelper.getUserById(userId)
        assertNotNull("Пользователь должен существовать после обновления", afterUpdate)
        assertEquals("Имя должно быть обновлено", "Обновленный", afterUpdate?.name)
        assertEquals("Вес должен быть обновлен", 75, afterUpdate?.weight)
        assertEquals("Цель по калориям должна быть обновлена", 2000, afterUpdate?.dailyCaloriesGoal)
    }

    @Test
    fun testAddWorkout() {
        val userId = 1L
        val workoutName = "Силовая тренировка"
        val workoutId = dbHelper.addWorkout(userId, workoutName)
        assertTrue("ID тренировки должен быть больше 0", workoutId > 0)
        val workout = dbHelper.getWorkoutById(workoutId)
        assertNotNull("Тренеровка должна быть найдена", workout)
        assertEquals("Название тренировки должно совпадать", workoutName, workout?.name)
        assertEquals("ID пользователя должно совпадать", userId, workout?.userId)
    }

    @Test
    fun testGetWorkoutsByUser() {
        val userId = 2L
        val workout1Id = dbHelper.addWorkout(userId, "Кардио")
        val workout2Id = dbHelper.addWorkout(userId, "Силовая")
        val workout3Id = dbHelper.addWorkout(userId, "Растяжка")
        val workouts = dbHelper.getWorkoutsByUser(userId)
        assertNotNull("Список тренировок не должен быть null", workouts)
        assertTrue("Должно быть минимум 3 тренировки", workouts.size >= 3)
        val workoutNames = workouts.map { it.name }
        assertTrue("Должна содержать 'Кардио'", workoutNames.contains("Кардио"))
        assertTrue("Должна содержать 'Силовая'", workoutNames.contains("Силовая"))
        assertTrue("Должна содержать 'Растяжка'", workoutNames.contains("Растяжка"))
    }

    @Test
    fun testAddExerciseToWorkout() {
        val userId = 3L
        val workoutId = dbHelper.addWorkout(userId, "Тестовая тренировка")
        val exercise = com.example.fitplan.Models.Exercise(
            id = 0L,
            workoutId = workoutId,
            name = "Приседания",
            sets = 3,
            reps = 10,
            weight = 50,
            rest = 60
        )

        val exerciseId = dbHelper.addExerciseToWorkout(workoutId, exercise)
        assertTrue("ID упражнения должен быть больше 0", exerciseId > 0)
        val exercises = dbHelper.getExercises(workoutId)
        assertNotNull("Список упражнений не должен быть null", exercises)
        assertFalse("Список упражнений не должен быть пустым", exercises.isEmpty())
        val firstExercise = exercises.firstOrNull()
        assertNotNull("Должно быть хотя бы одно упражнение", firstExercise)
        assertEquals("Название упражнения должно совпадать", "Приседания", firstExercise?.name)
        assertEquals("Количество подходов должно совпадать", 3, firstExercise?.sets)
        assertEquals("Количество повторений должно совпадать", 10, firstExercise?.reps)
    }

    @Test
    fun testWorkoutUpdate() {
        val userId = 4L
        val originalName = "Старое название"
        val newName = "Новое название"
        val workoutId = dbHelper.addWorkout(userId, originalName)
        dbHelper.updateWorkout(workoutId, newName)
        val updatedWorkout = dbHelper.getWorkoutById(workoutId)
        assertNotNull("Тренировка должна быть найдена", updatedWorkout)
        assertEquals("Название должно быть обновлено", newName, updatedWorkout?.name)
    }

    @Test
    fun testDatabaseSchema() {
        val db = dbHelper.readableDatabase
        val expectedTables = listOf(
            "users", "workouts", "exercises",
            "workout_exercises", "products", "nutrition_log", "nutrition_archive"
        )
        expectedTables.forEach { tableName ->
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'",
                null
            )
            assertTrue("Таблица '$tableName' должна существовать", cursor.count > 0)
            cursor.close()
        }
    }

    @Test
    fun testDatabaseVersion() {
        val db = dbHelper.readableDatabase
        val version = db.version
        assertTrue("Версия базы данных должна быть положительной", version > 0)
        assertEquals("Версия должна быть 8", 8, version)
    }

    @Test
    fun testUserNotFound() {
        val nonExistentUserId = 999999L
        val user = dbHelper.getUserById(nonExistentUserId)
        assertNull("Пользователь с ID $nonExistentUserId не должен существовать", user)
    }

    @Test
    fun testInvalidCredentials() {
        val email = "nonexistent@test.com"
        val password = "wrongpassword"
        val user = dbHelper.getUserByEmailAndPassword(email, password)
        assertNull("Пользователь с неверными данными не должен быть найден", user)
    }

    @Test
    fun testEmptyWorkoutsList() {
        val userId = 999L
        val workouts = dbHelper.getWorkoutsByUser(userId)

        assertNotNull("Список тренировок не должен быть null", workouts)
        assertTrue("Список тренировок должен быть пустым", workouts.isEmpty())
    }

    @Test
    fun testExerciseOperations() {
        val userId = 5L
        val workoutId = dbHelper.addWorkout(userId, "Для упражнений")

        val exercise1 = com.example.fitplan.Models.Exercise(
            id = 0L,
            workoutId = workoutId,
            name = "Жим лежа",
            sets = 4,
            reps = 8,
            weight = 80,
            rest = 90
        )
        val exercise2 = com.example.fitplan.Models.Exercise(
            id = 0L,
            workoutId = workoutId,
            name = "Тяга верхнего блока",
            sets = 3,
            reps = 12,
            weight = 40,
            rest = 60
        )
        dbHelper.addExerciseToWorkout(workoutId, exercise1)
        dbHelper.addExerciseToWorkout(workoutId, exercise2)
        val exercises = dbHelper.getExercises(workoutId)
        assertEquals("Должно быть 2 упражнения", 2, exercises.size)
        val exerciseNames = exercises.map { it.name }
        assertTrue("Должен содержать 'Жим лежа'", exerciseNames.contains("Жим лежа"))
        assertTrue("Должен содержать 'Тяга верхнего блока'", exerciseNames.contains("Тяга верхнего блока"))
    }

    @Test
    fun testDeleteExercises() {
        val userId = 6L
        val workoutId = dbHelper.addWorkout(userId, "Для удаления")
        val exercise = com.example.fitplan.Models.Exercise(
            id = 0L,
            workoutId = workoutId,
            name = "Удаляемое упражнение",
            sets = 1,
            reps = 1,
            weight = 0,
            rest = 0
        )
        dbHelper.addExerciseToWorkout(workoutId, exercise)
        var exercises = dbHelper.getExercises(workoutId)
        assertEquals("Должно быть 1 упражнение", 1, exercises.size)
        dbHelper.deleteExercisesByWorkout(workoutId)
        exercises = dbHelper.getExercises(workoutId)
        assertTrue("Список упражнений должен быть пустым после удаления", exercises.isEmpty())
    }
}