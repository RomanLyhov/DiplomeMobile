package com.example.fitplan.Utils

import com.example.fitplan.Models.User
import kotlin.math.roundToInt

object MacroCalculator {

    // Константы для уровней активности
    private const val SEDENTARY_MULTIPLIER = 1.2
    private const val LIGHT_ACTIVE_MULTIPLIER = 1.375
    private const val MODERATE_ACTIVE_MULTIPLIER = 1.55
    private const val VERY_ACTIVE_MULTIPLIER = 1.725
    private const val EXTRA_ACTIVE_MULTIPLIER = 1.9

    // Константы для целей
    private const val WEIGHT_LOSS_CALORIE_DEFICIT = 500 // дефицит 500 ккал
    private const val WEIGHT_GAIN_CALORIE_SURPLUS = 500 // профицит 500 ккал
    private const val PROTEIN_PER_KG = 1.8 // грамм белка на кг веса
    private const val FAT_PERCENTAGE = 0.25 // 25% калорий из жиров
    private const val CALORIES_PER_GRAM_PROTEIN = 4
    private const val CALORIES_PER_GRAM_FAT = 9
    private const val CALORIES_PER_GRAM_CARBS = 4

    data class MacroGoals(
        val calories: Int,
        val protein: Int,
        val fat: Int,
        val carbs: Int
    )

    fun calculateDailyGoals(user: User): MacroGoals? {
        // Проверяем наличие необходимых данных
        if (user.weight == null || user.height == null || user.age == null ||
            user.gender.isNullOrEmpty() || user.activity.isNullOrEmpty() ||
            user.goal.isNullOrEmpty()) {
            return null
        }

        // 1. Рассчитываем базальный метаболизм (BMR) по формуле Миффлина-Сан Жеора
        val bmr = calculateBMR(user)

        // 2. Рассчитываем TDEE с учетом активности
        val tdee = calculateTDEE(bmr, user.activity!!)

        // 3. Корректируем калории в зависимости от цели
        val targetCalories = adjustCaloriesForGoal(tdee, user.goal!!)

        // 4. Рассчитываем макронутриенты
        val proteinGrams = (user.weight * PROTEIN_PER_KG).roundToInt()
        val proteinCalories = proteinGrams * CALORIES_PER_GRAM_PROTEIN

        // Убеждаемся, что не делим на 0
        if (targetCalories <= 0) return null

        val fatCalories = (targetCalories * FAT_PERCENTAGE).roundToInt()
        val fatGrams = (fatCalories.toDouble() / CALORIES_PER_GRAM_FAT).roundToInt()

        val remainingCalories = targetCalories - proteinCalories - fatCalories
        // Проверяем, что осталось достаточно калорий для углеводов
        if (remainingCalories <= 0) {
            return MacroGoals(
                calories = targetCalories,
                protein = proteinGrams,
                fat = fatGrams,
                carbs = 0
            )
        }

        val carbsGrams = (remainingCalories.toDouble() / CALORIES_PER_GRAM_CARBS).roundToInt()

        return MacroGoals(
            calories = targetCalories,
            protein = proteinGrams,
            fat = fatGrams,
            carbs = carbsGrams
        )
    }

    private fun calculateBMR(user: User): Double {
        return try {
            if (user.gender.equals("male", ignoreCase = true)) {
                // Формула для мужчин: BMR = 10 × вес (кг) + 6.25 × рост (см) - 5 × возраст (лет) + 5
                10.0 * user.weight!! + 6.25 * user.height!! - 5.0 * user.age!! + 5.0
            } else {
                // Формула для женщин: BMR = 10 × вес (кг) + 6.25 × рост (см) - 5 × возраст (лет) - 161
                10.0 * user.weight!! + 6.25 * user.height!! - 5.0 * user.age!! - 161.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun calculateTDEE(bmr: Double, activityLevel: String): Int {
        val multiplier = when (activityLevel.lowercase()) {
            "sedentary" -> SEDENTARY_MULTIPLIER
            "light" -> LIGHT_ACTIVE_MULTIPLIER
            "moderate" -> MODERATE_ACTIVE_MULTIPLIER
            "active" -> VERY_ACTIVE_MULTIPLIER
            "very_active" -> EXTRA_ACTIVE_MULTIPLIER
            else -> SEDENTARY_MULTIPLIER
        }
        return (bmr * multiplier).roundToInt()
    }

    private fun adjustCaloriesForGoal(tdee: Int, goal: String): Int {
        return when (goal.lowercase()) {
            "weight_loss" -> {
                val result = tdee - WEIGHT_LOSS_CALORIE_DEFICIT
                if (result > 0) result else 1200 // Минимальное количество калорий
            }
            "weight_gain" -> tdee + WEIGHT_GAIN_CALORIE_SURPLUS
            "maintenance" -> tdee
            else -> tdee
        }
    }
}