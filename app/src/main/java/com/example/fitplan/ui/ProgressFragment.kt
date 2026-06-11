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
    private lateinit var containerAchievements: LinearLayout

    private var userId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view =
            inflater.inflate(R.layout.fragment_progress, container, false)

        tvWorkoutCount =
            view.findViewById(R.id.tvWorkoutCount)

        tvMaxWeight =
            view.findViewById(R.id.tvMaxWeight)

        containerRecords =
            view.findViewById(R.id.containerRecords)

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

                Log.d("PROGRESS", "DATA = $data")

                if (data.isEmpty()) {

                    tvWorkoutCount.text = "0"
                    tvMaxWeight.text = "0 кг"

                    return@launch
                }

                val allExercises =
                    data.flatMap { it.second }

                updateStatistics(
                    workoutsCount = data.size,
                    exercises = allExercises
                )

                showRecords(allExercises)

                showAchievements(
                    workoutsCount = data.size,
                    exercises = allExercises
                )

            } catch (e: Exception) {

                Log.e("PROGRESS_ERROR", "ERROR", e)

                Toast.makeText(
                    requireContext(),
                    "Ошибка загрузки прогресса",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateStatistics(
        workoutsCount: Int,
        exercises: List<WorkoutExerciseDto>
    ) {

        tvWorkoutCount.text =
            workoutsCount.toString()

        val maxWeight =
            exercises.maxOfOrNull { it.weight } ?: 0f

        tvMaxWeight.text =
            "$maxWeight кг"
    }

    private fun showRecords(
        exercises: List<WorkoutExerciseDto>
    ) {

        containerRecords.removeAllViews()

        val grouped = exercises.groupBy { it.exerciseId }

        grouped.forEach { (exerciseId, list) ->

            val card = layoutInflater.inflate(
                R.layout.record_item,
                containerRecords,
                false
            )

            val tvExerciseName = card.findViewById<TextView>(R.id.tvExerciseName)
            val tvRecordWeight = card.findViewById<TextView>(R.id.tvRecordWeight)
            val tvRecordReps = card.findViewById<TextView>(R.id.tvRecordReps)

            // значения рекордов
            val maxWeight = list.maxOfOrNull { it.weight } ?: 0f
            val maxReps = list.maxOfOrNull { it.reps } ?: 0

            tvRecordWeight.text = "Рекорд веса: $maxWeight кг"
            tvRecordReps.text = "Макс повторений: $maxReps"

            // получаем имя упражнения
            lifecycleScope.launch {
                val name = ApiManager.getExerciseName(exerciseId)
                tvExerciseName.text = name
            }

            containerRecords.addView(card)
        }
    }

    private fun showAchievements(
        workoutsCount: Int,
        exercises: List<WorkoutExerciseDto>
    ) {

        containerAchievements.removeAllViews()

        val achievements =
            mutableListOf<String>()

        if (workoutsCount >= 1) {
            achievements.add(" Первая тренировка")
        }

        if (workoutsCount >= 10) {
            achievements.add(" 10 тренировок")
        }

        if (workoutsCount >= 25) {
            achievements.add(" 25 тренировок")
        }

        val maxWeight =
            exercises.maxOfOrNull { it.weight } ?: 0f
        if (maxWeight >= 50) {
            achievements.add(" Поднял 50 кг")
        }

        if (maxWeight >= 100) {
            achievements.add(" Поднял 100 кг")
        }

        if (exercises.size >= 50) {
            achievements.add(
                "⚡ Выполнено 50 упражнений"
            )
        }

        achievements.forEach {

            val tv = TextView(
                requireContext()
            )

            tv.text = it
            tv.textSize = 16f
            tv.setPadding(
                0,
                12,
                0,
                12
            )

            containerAchievements
                .addView(tv)
        }
    }
}