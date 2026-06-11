package com.example.fitplan.ui

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.Api.ServerApiClient
import com.example.fitplan.Models.Exercise
import com.example.fitplan.Models.WorkoutDto
import com.example.fitplan.Models.WorkoutExerciseCreateDto
import com.example.fitplan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditWorkoutFragment : Fragment() {

    private var workoutId: Long = -1L
    private var workoutName: String = ""
    private val exercises = mutableListOf<Exercise>()

    private lateinit var container: LinearLayout
    private lateinit var etWorkoutName: EditText
    private lateinit var etExerciseName: EditText
    private lateinit var etSets: EditText
    private lateinit var etReps: EditText
    private lateinit var etWeight: EditText
    private var selectedExerciseId: Long? = null
    private lateinit var suggestionsContainer: LinearLayout
    private var searchJob: kotlinx.coroutines.Job? = null

    companion object {
        fun newInstance(workoutId: Long, workoutName: String): EditWorkoutFragment {
            return EditWorkoutFragment().apply {
                arguments = Bundle().apply {
                    putLong("workoutId", workoutId)
                    putString("workoutName", workoutName)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_create_workout, container, false)

        workoutId = arguments?.getLong("workoutId") ?: -1L
        workoutName = arguments?.getString("workoutName") ?: ""

        etWorkoutName = view.findViewById(R.id.etWorkoutName)
        etExerciseName = view.findViewById(R.id.etExerciseName)
        etSets = view.findViewById(R.id.etSets)
        etReps = view.findViewById(R.id.etReps)
        etWeight = view.findViewById(R.id.etWeight)
        suggestionsContainer = view.findViewById(R.id.suggestionsContainer)
        this.container = view.findViewById(R.id.containerExercises)



        // Заполняем название
        etWorkoutName.setText(workoutName)

        setupExerciseSearch()

        view.findViewById<Button>(R.id.createWorkoutButton).setOnClickListener {
            addExercise()
        }

        view.findViewById<Button>(R.id.btnCompleteWorkout).apply {
            text = "Сохранить изменения"
            setOnClickListener { saveChanges() }
        }

        // Загружаем существующие упражнения
        loadExistingExercises()

        return view
    }

    private fun loadExistingExercises() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val serverExercises = ApiManager.getExercises(workoutId)
                withContext(Dispatchers.Main) {
                    exercises.clear()
                    serverExercises.forEach { ex ->
                        exercises.add(
                            Exercise(
                                id = ex.exerciseId,
                                name = ex.name,
                                sets = ex.sets,
                                reps = ex.reps,
                                weight = ex.weight,
                                rest = ex.rest
                            )
                        )
                    }
                    render()
                }
            } catch (e: Exception) {
                Log.e("EDIT_WORKOUT", "load exercises error", e)
            }
        }
    }

    private fun addExercise() {
        val name = etExerciseName.text.toString().trim()
        if (name.isEmpty()) { toast("Введите упражнение"); return }

        val ex = Exercise(
            id = selectedExerciseId ?: 0,  // используем id из поиска
            name = name,
            sets = etSets.text.toString().toIntOrNull() ?: 3,
            reps = etReps.text.toString().toIntOrNull() ?: 10,
            weight = etWeight.text.toString().toFloatOrNull() ?: 0f,
            rest = 90
        )
        selectedExerciseId = null  // сбрасываем
        exercises.add(ex)
        render()

        etExerciseName.text.clear()
        etSets.setText("")
        etReps.setText("")
        etWeight.setText("")
    }

    private fun saveChanges() {
        val name = etWorkoutName.text.toString().trim()
        if (name.isEmpty()) { toast("Введите название"); return }
        if (exercises.isEmpty()) { toast("Добавьте упражнения"); return }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Обновляем название тренировки
                ServerApiClient.apiService.updateWorkout(
                    workoutId,
                    WorkoutDto(id = workoutId, userId = 0, name = name)
                )

                // 2. Удаляем старые упражнения через DELETE /workouts/:id/exercises
                ServerApiClient.apiService.deleteWorkoutExercises(workoutId)

                // 3. Добавляем новые
                exercises.forEach { ex ->
                    val dto = WorkoutExerciseCreateDto(
                        workoutId = workoutId,
                        exerciseId = if (ex.id > 0) ex.id else null,
                        name = ex.name,
                        sets = ex.sets,
                        reps = ex.reps,
                        weight = ex.weight,
                        rest = ex.rest
                    )
                    ApiManager.createExerciseWithWorkout(dto)
                }

                withContext(Dispatchers.Main) {
                    toast("Изменения сохранены")
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                Log.e("EDIT_WORKOUT", "save error", e)
                withContext(Dispatchers.Main) { toast("Ошибка сохранения") }
            }
        }
    }

    private fun setupExerciseSearch() {
        etExerciseName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim()
                searchJob?.cancel()
                if (query.length < 2) { suggestionsContainer.visibility = View.GONE; return }
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    try {
                        val response = withContext(Dispatchers.IO) {
                            ServerApiClient.apiService.searchExercises(query)
                        }
                        val results = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                        suggestionsContainer.removeAllViews()
                        if (results.isEmpty()) { suggestionsContainer.visibility = View.GONE; return@launch }
                        suggestionsContainer.visibility = View.VISIBLE
                        results.forEach { ex ->
                            val tv = TextView(requireContext()).apply {
                                text = ex.name
                                textSize = 14f
                                setPadding(32, 24, 32, 24)
                                setTextColor(resources.getColor(R.color.text_primary, null))
                                setBackgroundResource(android.R.drawable.list_selector_background)
                                setOnClickListener {
                                    etExerciseName.setText(ex.name)
                                    selectedExerciseId = ex.id
                                    ex.name?.let { etExerciseName.setSelection(it.length) }
                                    suggestionsContainer.visibility = View.GONE
                                }
                            }
                            suggestionsContainer.addView(tv)
                        }
                    } catch (e: Exception) {
                        Log.e("EDIT_SEARCH", "error", e)
                    }
                }
            }
        })
    }

    private fun render() {
        container.removeAllViews()
        exercises.forEachIndexed { index, ex ->
            val v = layoutInflater.inflate(R.layout.exercise_card, container, false)
            v.findViewById<TextView>(R.id.tvExerciseName).text = ex.name
            v.findViewById<TextView>(R.id.tvSets).text = ex.sets.toString()
            v.findViewById<TextView>(R.id.tvReps).text = ex.reps.toString()
            v.findViewById<TextView>(R.id.btnRemoveExercise).apply {
                visibility = View.VISIBLE
                setOnClickListener { exercises.removeAt(index); render() }
            }
            container.addView(v)
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}