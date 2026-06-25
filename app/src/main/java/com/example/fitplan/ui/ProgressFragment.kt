package com.example.fitplan.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.WorkoutExerciseDto
import com.example.fitplan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProgressFragment : Fragment() {

    private lateinit var tvWorkoutCount: TextView
    private lateinit var tvMaxWeight: TextView
    private lateinit var containerRecords: LinearLayout

    private var userId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_progress, container, false)

        tvWorkoutCount = view.findViewById(R.id.tvWorkoutCount)
        tvMaxWeight = view.findViewById(R.id.tvMaxWeight)
        containerRecords = view.findViewById(R.id.containerRecords)

        return view
    }

    override fun onResume() {
        super.onResume()

        if (!isAdded || context == null) return

        userId = requireContext()
            .getSharedPreferences("session", Context.MODE_PRIVATE)
            .getLong("server_user_id", -1L)

        loadProgress()
    }

    private fun loadProgress() {

        if (userId == -1L) {
            Toast.makeText(
                requireContext(),
                "Пользователь не найден",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        lifecycleScope.launch {

            try {

                val data = withContext(Dispatchers.IO) {
                    ApiManager.getWorkoutsFullClient(userId)
                }

                Log.d("PROGRESS", "DATA size = ${data.size}")

                // Очищаем контейнер перед загрузкой
                containerRecords.removeAllViews()

                if (data.isEmpty()) {
                    tvWorkoutCount.text = "0"
                    tvMaxWeight.text = "0 кг"
                    showEmptyState()
                    return@launch
                }

                val allExercises = data.flatMap { it.second }

                updateStatistics(
                    workoutsCount = data.size,
                    exercises = allExercises
                )

                showRecords(allExercises)

            } catch (e: Exception) {

                Log.e("PROGRESS_ERROR", "ERROR", e)

                // Очищаем контейнер при ошибке
                containerRecords.removeAllViews()

                val errorMsg = when {
                    e.message?.contains("SocketTimeoutException") == true ->
                        "Таймаут соединения"
                    e.message?.contains("UnknownHostException") == true ->
                        "Нет интернета"
                    else ->
                        "Ошибка: ${e.message}"
                }

                // Показываем ошибку в контейнере рекордов
                val tvError = TextView(requireContext()).apply {
                    text = "$errorMsg\n\nПопробуйте обновить страницу"
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 40, 0, 40)
                    setTextColor(resources.getColor(android.R.color.holo_red_dark))
                }
                containerRecords.addView(tvError)

                Toast.makeText(
                    requireContext(),
                    "Ошибка загрузки: $errorMsg",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showEmptyState() {
        containerRecords.removeAllViews()

        val tvEmpty = TextView(requireContext()).apply {
            text = "Нет данных о тренировках\n\nНачните тренироваться, чтобы видеть свой прогресс!"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 40)
            setTextColor(resources.getColor(android.R.color.darker_gray))
        }
        containerRecords.addView(tvEmpty)
    }

    private fun updateStatistics(
        workoutsCount: Int,
        exercises: List<WorkoutExerciseDto>
    ) {
        tvWorkoutCount.text = workoutsCount.toString()

        val maxWeight = exercises.maxOfOrNull { it.weight } ?: 0f
        tvMaxWeight.text = "${maxWeight.toInt()} кг"
    }

    private fun showRecords(exercises: List<WorkoutExerciseDto>) {
        containerRecords.removeAllViews()

        // Группируем по exerciseId
        val grouped = exercises.groupBy { it.exerciseId }

        if (grouped.isEmpty()) {
            val tvEmpty = TextView(requireContext()).apply {
                text = "📭 Нет записей упражнений"
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 20, 0, 20)
                setTextColor(resources.getColor(android.R.color.darker_gray))
            }
            containerRecords.addView(tvEmpty)
            return
        }

        // Сортируем по названию упражнения (для красоты)
        val sortedEntries = grouped.entries.sortedBy { it.key }

        sortedEntries.forEach { (exerciseId, list) ->

            // Создаем карточку для каждого упражнения
            val cardView = layoutInflater.inflate(
                R.layout.item_record_card,
                containerRecords,
                false
            ) as CardView

            val tvExerciseName = cardView.findViewById<TextView>(R.id.tvExerciseName)
            val tvRecordWeight = cardView.findViewById<TextView>(R.id.tvRecordWeight)
            val tvRecordReps = cardView.findViewById<TextView>(R.id.tvRecordReps)

            // Получаем рекорды
            val maxWeight = list.maxOfOrNull { it.weight } ?: 0f
            val maxReps = list.maxOfOrNull { it.reps } ?: 0
            val totalSets = list.sumOf { it.sets }

            // Показываем временное имя
            tvExerciseName.text = "Загрузка..."
            tvRecordWeight.text = " Рекорд веса: ${maxWeight.toInt()} кг"
            tvRecordReps.text = "Макс повторений: $maxReps (всего подходов: $totalSets)"

            // Загружаем имя асинхронно
            lifecycleScope.launch {
                try {
                    val name = ApiManager.getExerciseName(exerciseId)
                    tvExerciseName.text = name
                } catch (e: Exception) {
                    Log.e("PROGRESS", "Error loading exercise name", e)
                    tvExerciseName.text = "Упражнение #$exerciseId"
                }
            }

            containerRecords.addView(cardView)
        }
    }
}