package com.example.fitplan.ui

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.Models.Api.ServerApiClient
import com.example.fitplan.Models.Api.WorkoutHistoryDto
import com.example.fitplan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WorkoutHistoryFragment : Fragment() {

    private lateinit var containerHistory: LinearLayout
    private lateinit var tvSelectedDate: TextView
    private var selectedDate: Calendar = Calendar.getInstance()
    private var allHistory: List<WorkoutHistoryDto> = emptyList()
    private var loadJob: Job? = null

    private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_workout_history, container, false)

        containerHistory = view.findViewById(R.id.containerHistory)
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate)
        val btnSelectDate = view.findViewById<Button>(R.id.btnSelectDate)
        val btnBack = view.findViewById<android.widget.ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Загружаем данные только один раз при создании view
        loadAllHistory()
    }

    private fun showDatePicker() {
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate.set(year, month, dayOfMonth, 0, 0, 0)
                selectedDate.set(Calendar.MILLISECOND, 0)
                updateDisplayedDate()
                filterHistoryByDate()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDisplayedDate() {
        tvSelectedDate.text = dateFormat.format(selectedDate.time)
    }

    private fun loadAllHistory() {
        // Отменяем предыдущую загрузку
        loadJob?.cancel()

        val userId = requireContext()
            .getSharedPreferences("session", 0)
            .getLong("server_user_id", -1L)

        if (userId == -1L) {
            Toast.makeText(requireContext(), "Ошибка: пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        // Показываем загрузку
        containerHistory.removeAllViews()
        val loadingView = TextView(requireContext()).apply {
            text = "Загрузка истории..."
            textSize = 16f
            setTextColor(Color.GRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 100, 0, 100)
        }
        containerHistory.addView(loadingView)

        loadJob = lifecycleScope.launch {
            try {
                Log.d("HISTORY", "Starting to load history for userId: $userId")

                allHistory = withContext(Dispatchers.IO) {
                    val response = ServerApiClient.apiService.getWorkoutHistory(
                        userId = userId,
                        startDate = null,
                        endDate = null
                    )
                    if (response.isSuccessful) {
                        val data = response.body() ?: emptyList()
                        Log.d("HISTORY", "Loaded ${data.size} records from server")
                        data.forEachIndexed { index, record ->
                            Log.d("HISTORY", "Record $index: name='${record.workoutName}', date=${record.completedAt}, fully=${record.isFullyDone}")
                        }
                        data
                    } else {
                        Log.e("HISTORY", "Error: ${response.code()} - ${response.errorBody()?.string()}")
                        emptyList()
                    }
                }

                // Обновляем отображение даты
                updateDisplayedDate()

                // Показываем данные
                if (allHistory.isNotEmpty()) {
                    Log.d("HISTORY", "Showing ${allHistory.size} records")
                    displayHistory(allHistory) // Показываем все записи сразу
                } else {
                    Log.d("HISTORY", "No records found")
                    displayHistory(emptyList())
                }

            } catch (e: Exception) {
                Log.e("HISTORY", "Error loading history", e)
                containerHistory.removeAllViews()
                val errorView = TextView(requireContext()).apply {
                    text = "Ошибка загрузки: ${e.message}"
                    textSize = 14f
                    setTextColor(Color.RED)
                    gravity = android.view.Gravity.CENTER
                    setPadding(32, 100, 32, 100)
                }
                containerHistory.addView(errorView)
            }
        }
    }

    private fun filterHistoryByDate() {
        // Получаем начало и конец выбранного дня в миллисекундах
        val startOfDay = selectedDate.timeInMillis
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000)

        Log.d("HISTORY", "Filtering by date: start=$startOfDay, end=$endOfDay")
        Log.d("HISTORY", "Total records: ${allHistory.size}")

        // Фильтруем историю по выбранной дате
        val filteredHistory = allHistory.filter { history ->
            history.completedAt in startOfDay until endOfDay
        }

        Log.d("HISTORY", "Filtered records: ${filteredHistory.size}")

        // Отображаем отфильтрованные данные
        displayHistory(filteredHistory)
    }

    private fun displayHistory(history: List<WorkoutHistoryDto>) {
        containerHistory.removeAllViews()

        Log.d("HISTORY", "displayHistory called with ${history.size} records")

        if (history.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = if (allHistory.isEmpty()) {
                    "Нет завершённых тренировок\n\nВыполните тренировку, чтобы она появилась здесь"
                } else {
                    "Нет тренировок за ${dateFormat.format(selectedDate.time)}"
                }
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
                setPadding(32, 100, 32, 100)
            }
            containerHistory.addView(emptyView)
            return
        }

        // Сортируем по времени (сначала новые)
        history.sortedByDescending { it.completedAt }.forEach { record ->
            addHistoryCard(record)
        }

        // Показываем статистику за день
        addDailyStatsCard(history)
    }

    private fun addHistoryCard(history: WorkoutHistoryDto) {
        val card = layoutInflater.inflate(
            R.layout.item_workout_history_card,
            containerHistory,
            false
        )

        val tvName = card.findViewById<TextView>(R.id.tvName)
        val tvTime = card.findViewById<TextView>(R.id.tvTime)
        val tvStatus = card.findViewById<TextView>(R.id.tvStatus)
        val tvExercises = card.findViewById<TextView>(R.id.tvExercises)
        val tvPercent = card.findViewById<TextView>(R.id.tvPercent)
        val progress = card.findViewById<ProgressBar>(R.id.progress)

        tvName.text = history.workoutName.ifEmpty { "Тренировка" }

        val timeFormat = SimpleDateFormat("HH:mm", Locale("ru"))
        tvTime.text = timeFormat.format(Date(history.completedAt))

        val percent = if (history.totalExercises > 0)
            (history.completedExercises * 100 / history.totalExercises)
        else 0

        progress.progress = percent
        tvPercent.text = "$percent%"

        tvExercises.text = "Упражнения: ${history.completedExercises}/${history.totalExercises}"

        if (history.isFullyDone) {
            tvStatus.text = "✔ Выполнено полностью"
            tvStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvStatus.text = "⚠ Выполнено частично"
            tvStatus.setTextColor(Color.parseColor("#FF9800"))
        }

        containerHistory.addView(card)
    }

    private fun addDailyStatsCard(history: List<WorkoutHistoryDto>) {
        val card = layoutInflater.inflate(
            R.layout.item_daily_stats_card,
            containerHistory,
            false
        )

        val tvWorkouts = card.findViewById<TextView>(R.id.tvWorkouts)
        val tvFullyDone = card.findViewById<TextView>(R.id.tvFullyDone)
        val tvExercises = card.findViewById<TextView>(R.id.tvExercises)
        val tvPercent = card.findViewById<TextView>(R.id.tvPercent)
        val progress = card.findViewById<ProgressBar>(R.id.progressDay)

        val totalWorkouts = history.size
        val fullyDone = history.count { it.isFullyDone }
        val totalExercises = history.sumOf { it.totalExercises }
        val completedExercises = history.sumOf { it.completedExercises }

        val percent = if (totalExercises > 0)
            (completedExercises * 100 / totalExercises)
        else 0

        tvWorkouts.text = "🏋️ Тренировок: $totalWorkouts"
        tvFullyDone.text = "✅ Полностью: $fullyDone"
        tvExercises.text = "📋 Упражнений: $completedExercises/$totalExercises"

        tvPercent.text = "$percent%"
        progress.progress = percent

        containerHistory.addView(card)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
    }
}