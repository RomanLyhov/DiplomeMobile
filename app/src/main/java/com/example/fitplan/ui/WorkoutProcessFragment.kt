package com.example.fitplan.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.Api.ServerApiClient
import com.example.fitplan.Models.Api.WorkoutHistoryDto
import com.example.fitplan.Models.Exercise
import com.example.fitplan.Models.WorkoutExerciseDto
import com.example.fitplan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class WorkoutProcessFragment : Fragment() {

    private var workoutId: Long = -1L
    private var workoutName: String = ""
    private var userId: Long = -1L
    private lateinit var containerExercises: LinearLayout

    // Добавьте это поле
    private var loadJob: Job? = null

    private val exerciseCompletion = mutableMapOf<Int, Boolean>()
    private var totalExercises = 0
    private val activeTimers = mutableListOf<CountDownTimer>()

    companion object {
        fun newInstance(workoutId: Long, workoutName: String): WorkoutProcessFragment {
            return WorkoutProcessFragment().apply {
                arguments = Bundle().apply {
                    putLong("workoutId", workoutId)
                    putString("workoutName", workoutName)
                }
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_workout_process, container, false)

        workoutId = arguments?.getLong("workoutId", -1L) ?: -1L
        workoutName = arguments?.getString("workoutName", "") ?: ""
        userId = requireContext()
            .getSharedPreferences("session", 0)
            .getLong("server_user_id", -1L)

        containerExercises = view.findViewById(R.id.containerExercises)

        view.findViewById<Button>(R.id.btnBack).setOnClickListener {
            activeTimers.forEach { it.cancel() }
            parentFragmentManager.popBackStack()
        }

        view.findViewById<Button>(R.id.btnFinishWorkout).setOnClickListener {
            finishWorkout()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadExercisesFromServer()
    }

    private fun loadExercisesFromServer() {
        // Отменяем предыдущую загрузку если есть
        loadJob?.cancel()

        loadJob = lifecycleScope.launch {
            try {
                Log.d("PROCESS", "Starting to load exercises for workoutId=$workoutId")

                val exercises = withContext(Dispatchers.IO) {
                    ApiManager.getExercises(workoutId)
                }

                Log.d("PROCESS", "Loaded ${exercises.size} exercises")

                // Важно: проверяем что фрагмент еще активен
                if (!isAdded || view == null) {
                    Log.d("PROCESS", "Fragment not active, skipping UI update")
                    return@launch
                }

                totalExercises = exercises.size

                if (exercises.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "У тренировки нет упражнений",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Очищаем контейнер перед добавлением
                containerExercises.removeAllViews()

                exercises.forEachIndexed { index, ex ->
                    Log.d("PROCESS", "Adding exercise ${index+1}: ${ex.name}")
                    addExerciseCard(ex, index)
                }

                Log.d("PROCESS", "All exercises added, total=${totalExercises}")

            } catch (e: CancellationException) {
                Log.d("PROCESS", "Loading cancelled")
            } catch (e: Exception) {
                Log.e("PROCESS", "load error", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun addExerciseCard(ex: WorkoutExerciseDto, exerciseIndex: Int) {
        // Проверяем что фрагмент активен
        if (!isAdded || view == null) return

        val card = layoutInflater.inflate(R.layout.item_workout_exercise, containerExercises, false)

        // Убедитесь, что ID в вашем XML файле совпадают
        val tvExerciseName = card.findViewById<TextView>(R.id.tvExerciseName)
        val tvSets = card.findViewById<TextView>(R.id.tvSets)
        val tvReps = card.findViewById<TextView>(R.id.tvReps)
        val tvWeight = card.findViewById<TextView>(R.id.tvWeight)
        val tvRest = card.findViewById<TextView>(R.id.tvRest)
        val containerSets = card.findViewById<LinearLayout>(R.id.containerSets)
        val tvTimer = card.findViewById<TextView>(R.id.tvTimer)
        val btnRest = card.findViewById<Button>(R.id.btnRest)

        // Устанавливаем значения с проверкой на null
        tvExerciseName.text = ex.name.ifEmpty { "Упражнение" }
        tvSets.text = ex.sets.toString()
        tvReps.text = ex.reps.toString()
        tvWeight.text = ex.weight.toString()
        tvRest.text = ex.rest.toString()

        Log.d("PROCESS", "Setting up card: ${ex.name}, sets=${ex.sets}, reps=${ex.reps}")

        val setsDone = BooleanArray(ex.sets)

        // Добавляем кружки для подходов
        containerSets.removeAllViews()
        repeat(ex.sets) { index ->
            val setView = TextView(requireContext()).apply {
                text = (index + 1).toString()
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val size = (36 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    if (index < ex.sets - 1) marginEnd = 8
                }
                background = createCircleDrawable(Color.parseColor("#2196F3"))
            }

            var clicks = 0
            setView.setOnClickListener {
                if (setsDone[index]) return@setOnClickListener
                clicks++
                if (clicks >= 2) {
                    setsDone[index] = true
                    setView.background = createCircleDrawable(Color.parseColor("#4CAF50"))

                    if (setsDone.all { it }) {
                        exerciseCompletion[exerciseIndex] = true
                        card.setBackgroundColor(Color.parseColor("#1A4CAF50"))
                    }
                }
            }
            containerSets.addView(setView)
        }

        // Настройка таймера
        var currentTimer: CountDownTimer? = null
        var isRunning = false
        var remaining = ex.rest * 1000L

        tvTimer.text = formatTime(remaining)

        btnRest.setOnClickListener {
            if (!isRunning) {
                currentTimer = object : CountDownTimer(remaining, 1000) {
                    override fun onTick(ms: Long) {
                        remaining = ms
                        tvTimer.text = formatTime(ms)
                    }
                    override fun onFinish() {
                        tvTimer.text = "Готово!"
                        isRunning = false
                        remaining = ex.rest * 1000L
                        btnRest.text = "Отдых"
                    }
                }.start()
                activeTimers.add(currentTimer!!)
                isRunning = true
                btnRest.text = "Стоп"
            } else {
                currentTimer?.cancel()
                isRunning = false
                remaining = ex.rest * 1000L
                tvTimer.text = formatTime(remaining)
                btnRest.text = "Отдых"
            }
        }

        containerExercises.addView(card)
    }

    private fun finishWorkout() {
        val completedCount = exerciseCompletion.values.count { it }
        val isFullyDone = completedCount == totalExercises && totalExercises > 0

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ServerApiClient.apiService.saveWorkoutHistory(
                    WorkoutHistoryDto(
                        userId = userId,
                        workoutId = workoutId,
                        workoutName = workoutName,
                        completedAt = System.currentTimeMillis(),
                        totalExercises = totalExercises,
                        completedExercises = completedCount,
                        isFullyDone = isFullyDone
                    )
                )
                withContext(Dispatchers.Main) {
                    val msg = if (isFullyDone) "Тренировка выполнена! 💪" else "Сохранено ($completedCount/$totalExercises)"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    activeTimers.forEach { it.cancel() }
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                Log.e("PROCESS", "save error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        activeTimers.forEach { it.cancel() }
        activeTimers.clear()
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return String.format("%02d:%02d", s / 60, s % 60)
    }

    private fun createCircleDrawable(color: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
}