package com.example.fitplan.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.Models.Api.ServerApiClient
import com.example.fitplan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExerciseDetailsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_exercise_details, container, false)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = view.findViewById<TextView>(R.id.tvDescription)
        val btnBack = view.findViewById<android.widget.ImageButton>(R.id.btnBack)

        // 🔥 ИСПРАВЛЕНО: читаем правильные ключи
        val exerciseId = arguments?.getLong("exercise_id", -1L) ?: -1L
        val exerciseName = arguments?.getString("exercise_name") ?: ""

        Log.d("EXERCISE_DETAILS", "Exercise ID: $exerciseId, Name: $exerciseName")

        tvTitle.text = exerciseName
        tvDescription.text = "Загрузка описания..."

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        if (exerciseId == -1L && exerciseName.isEmpty()) {
            tvDescription.text = "Нет данных об упражнении"
            return view
        }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    if (exerciseId != -1L) {
                        // Загружаем по ID
                        ServerApiClient.apiService.getExerciseById(exerciseId)
                    } else {
                        // Ищем по имени
                        val searchResponse = ServerApiClient.apiService.searchExercises(exerciseName)
                        if (searchResponse.isSuccessful) {
                            val exercises = searchResponse.body() ?: emptyList()
                            val found = exercises.find { it.name.equals(exerciseName, ignoreCase = true) }
                            if (found != null) {
                                ServerApiClient.apiService.getExerciseById(found.id)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (response != null && response.isSuccessful) {
                        val exercise = response.body()
                        if (exercise != null) {
                            tvTitle.text = exercise.name ?: exerciseName
                            val instruction = exercise.instruction ?: "Описание отсутствует"
                            tvDescription.text = instruction
                            Log.d("EXERCISE_DETAILS", "Instruction loaded: ${instruction.take(100)}")
                        } else {
                            tvDescription.text = "Упражнение не найдено"
                        }
                    } else {
                        tvDescription.text = "Ошибка загрузки: упражнение не найдено"
                    }
                }
            } catch (e: Exception) {
                Log.e("EXERCISE_DETAILS", "Error", e)
                withContext(Dispatchers.Main) {
                    tvDescription.text = "Ошибка: ${e.message}"
                }
            }
        }

        return view
    }
}