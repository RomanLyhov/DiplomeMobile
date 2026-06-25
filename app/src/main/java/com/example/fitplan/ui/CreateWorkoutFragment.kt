package com.example.fitplan.ui

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.Api.ExerciseProgressDto
import com.example.fitplan.Models.Api.ServerApiClient
import com.example.fitplan.Models.Exercise
import com.example.fitplan.Models.WorkoutDto
import com.example.fitplan.Models.WorkoutExerciseCreateDto
import com.example.fitplan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

class CreateWorkoutFragment : Fragment() {

    private val exercises = mutableListOf<Exercise>()

    private lateinit var container: LinearLayout
    private lateinit var etWorkoutName: EditText
    private lateinit var etExerciseName: EditText
    private lateinit var etSets: EditText
    private lateinit var etReps: EditText
    private lateinit var etWeight: EditText
    private lateinit var suggestionsContainer: LinearLayout
    private var selectedExerciseId: Long? = null
    private lateinit var etRest: EditText
    private var searchJob: kotlinx.coroutines.Job? = null

    // Сохраняем название тренировки отдельно
    private var savedWorkoutName: String = ""
    private var savedExercises: ArrayList<Exercise> = arrayListOf()

    companion object {
        private const val KEY_WORKOUT_NAME = "workout_name"
        private const val KEY_EXERCISES = "exercises"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Восстанавливаем данные после поворота экрана
        if (savedInstanceState != null) {
            savedWorkoutName = savedInstanceState.getString(KEY_WORKOUT_NAME, "")
            val restored = savedInstanceState.getSerializable(KEY_EXERCISES) as? ArrayList<Exercise>
            if (restored != null) {
                savedExercises.clear()
                savedExercises.addAll(restored)
                exercises.clear()
                exercises.addAll(restored)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Сохраняем данные перед поворотом экрана
        if (::etWorkoutName.isInitialized) {
            outState.putString(KEY_WORKOUT_NAME, etWorkoutName.text.toString())
        } else {
            outState.putString(KEY_WORKOUT_NAME, savedWorkoutName)
        }
        outState.putSerializable(KEY_EXERCISES, ArrayList(exercises))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_create_workout, container, false)
        val btnBack = view.findViewById<android.widget.ImageButton>(R.id.btnBack)

        etWorkoutName = view.findViewById(R.id.etWorkoutName)
        etExerciseName = view.findViewById(R.id.etExerciseName)
        etSets = view.findViewById(R.id.etSets)
        etReps = view.findViewById(R.id.etReps)
        etWeight = view.findViewById(R.id.etWeight)
        etRest = view.findViewById(R.id.etRest)
        suggestionsContainer = view.findViewById(R.id.suggestionsContainer)

        // Восстанавливаем название тренировки
        if (savedWorkoutName.isNotEmpty()) {
            etWorkoutName.setText(savedWorkoutName)
        }

        this.container = view.findViewById(R.id.containerExercises)

        setupExerciseSearch()

        view.findViewById<Button>(R.id.createWorkoutButton).setOnClickListener {
            addExercise()
        }

        view.findViewById<Button>(R.id.btnCompleteWorkout).setOnClickListener {
            saveWorkout()
        }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Восстанавливаем отображение списка упражнений
        if (exercises.isNotEmpty()) {
            render()
        }

        return view
    }

    private fun addExercise() {
        val name = etExerciseName.text.toString().trim()
        if (name.isEmpty()) {
            toast("Введите упражнение")
            return
        }

        val sets = etSets.text.toString().toIntOrNull() ?: 3
        val reps = etReps.text.toString().toIntOrNull() ?: 10
        val weight = etWeight.text.toString().toFloatOrNull() ?: 0f
        val rest = etRest.text.toString().toIntOrNull() ?: 90

        val ex = Exercise(
            id = selectedExerciseId ?: 0,
            name = name,
            sets = sets,
            reps = reps,
            weight = weight,
            rest = rest
        )

        exercises.add(ex)
        selectedExerciseId = null
        render()

        etExerciseName.text.clear()
        etSets.setText("")
        etReps.setText("")
        etWeight.setText("")
        etRest.setText("")
    }

    private fun saveWorkout() {
        val name = etWorkoutName.text.toString().trim()
        if (name.isEmpty()) { toast("Введите название"); return }
        if (exercises.isEmpty()) { toast("Добавьте упражнения"); return }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val prefs = requireContext().getSharedPreferences("session", 0)
                val userId = prefs.getLong("server_user_id", -1L)

                if (userId == -1L) {
                    withContext(Dispatchers.Main) { toast("Нет server_user_id") }
                    return@launch
                }

                val workoutId = ApiManager.addWorkout(
                    WorkoutDto(id = 0, userId = userId, name = name)
                )

                if (workoutId == null) {
                    withContext(Dispatchers.Main) { toast("Ошибка создания тренировки") }
                    return@launch
                }

                Log.d("CREATE_WORKOUT", "WORKOUT ID = $workoutId")

                exercises.forEach { ex ->
                    val dto = WorkoutExerciseCreateDto(
                        workoutId = workoutId,
                        name = ex.name,
                        sets = ex.sets,
                        reps = ex.reps,
                        weight = ex.weight,
                        rest = ex.rest,
                        exerciseId = if (ex.id > 0) ex.id else null
                    )
                    val success = ApiManager.createExerciseWithWorkout(dto)
                    Log.d("CREATE_WORKOUT", "exercise ${ex.name} success=$success")

                    // === ЛОГИКА СОХРАНЕНИЯ НАЧАЛЬНОГО ВЕСА ===
                    if (ex.weight > 0) {
                        // Проверяем, есть ли уже начальный вес для этого упражнения
                        val initialWeight = ApiManager.getExerciseInitialWeight(userId, ex.id)

                        if (initialWeight == null || initialWeight == 0f) {
                            // НЕТ начального веса → сохраняем как начальный
                            val progressDto = ExerciseProgressDto(
                                userId = userId,
                                exerciseId = ex.id,
                                workoutId = workoutId,
                                weight = ex.weight,
                                reps = ex.reps,
                                sets = ex.sets,
                                notes = "🏁 Начальный вес (зафиксирован)"
                            )
                            ApiManager.saveExerciseProgress(progressDto)
                            Log.d("CREATE_WORKOUT", "Initial weight saved for ${ex.name}: ${ex.weight} кг")
                        } else {
                            // УЖЕ ЕСТЬ начальный вес → сохраняем как прогресс
                            val progressDto = ExerciseProgressDto(
                                userId = userId,
                                exerciseId = ex.id,
                                workoutId = workoutId,
                                weight = ex.weight,
                                reps = ex.reps,
                                sets = ex.sets,
                                notes = "Прогресс от ${System.currentTimeMillis()}"
                            )
                            ApiManager.saveExerciseProgress(progressDto)
                            Log.d("CREATE_WORKOUT", "Progress saved for ${ex.name}: ${ex.weight} кг")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    toast("Тренировка создана")
                    parentFragmentManager.popBackStack()
                }

            } catch (e: Exception) {
                Log.e("CREATE_WORKOUT", "error", e)
                withContext(Dispatchers.Main) { toast("Ошибка: ${e.message}") }
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

                if (query.length < 2) {
                    suggestionsContainer.visibility = View.GONE
                    return
                }

                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    try {
                        val response = withContext(Dispatchers.IO) {
                            ServerApiClient.apiService.searchExercises(query)
                        }
                        val results = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()

                        suggestionsContainer.removeAllViews()
                        if (results.isEmpty()) {
                            suggestionsContainer.visibility = View.GONE
                            return@launch
                        }
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
                                    ex.name?.let { it1 -> etExerciseName.setSelection(it1.length) }
                                    suggestionsContainer.visibility = View.GONE
                                }
                            }
                            suggestionsContainer.addView(tv)
                        }
                    } catch (e: Exception) {
                        Log.e("EXERCISE_SEARCH", "error", e)
                    }
                }
            }
        })
    }

    private fun render() {
        container.removeAllViews()
        val userId = getUserId()

        exercises.forEachIndexed { index, ex ->
            val cardView = layoutInflater.inflate(R.layout.exercise_card, container, false) as LinearLayout

            val tvName = cardView.findViewById<TextView>(R.id.tvExerciseName)
            val tvSets = cardView.findViewById<TextView>(R.id.tvSets)
            val tvReps = cardView.findViewById<TextView>(R.id.tvReps)
            val tvWeight = cardView.findViewById<TextView>(R.id.tvWeight)
            val tvRest = cardView.findViewById<TextView>(R.id.tvRest)

            tvName.text = ex.name
            tvSets.text = ex.sets.toString()
            tvReps.text = ex.reps.toString()
            tvWeight.text = ex.weight.toString()
            tvRest.text = "${ex.rest} сек"

            // === ПОКАЗЫВАЕМ НАЧАЛЬНЫЙ ВЕС ===
            val badge = cardView.findViewById<TextView>(R.id.tvInitialWeightBadge)

            lifecycleScope.launch {
                val initialWeight = ApiManager.getExerciseInitialWeight(userId, ex.id)
                if (initialWeight != null && initialWeight > 0) {
                    badge.text = "🏁 Начальный: ${initialWeight} кг"
                    badge.visibility = View.VISIBLE

                    val diff = ex.weight - initialWeight
                    if (diff > 0.01) {
                        badge.append("  ↑ +${"%.1f".format(diff)} кг")
                        badge.setTextColor(resources.getColor(R.color.green, null))
                    } else if (diff < -0.01) {
                        badge.append("  ↓ ${"%.1f".format(diff)} кг")
                        badge.setTextColor(resources.getColor(R.color.red, null))
                    } else {
                        badge.append("  → без изменений")
                        badge.setTextColor(resources.getColor(R.color.text_hint, null))
                    }
                } else {
                    badge.visibility = View.GONE
                }
            }

            // Кнопка удаления
            val btnRemove = cardView.findViewById<TextView>(R.id.btnRemoveExercise)
            btnRemove?.visibility = View.VISIBLE
            btnRemove?.setOnClickListener {
                exercises.removeAt(index)
                render()
            }

            // Открытие деталей упражнения
            cardView.setOnClickListener {
                val fragment = ExerciseDetailsFragment()
                fragment.arguments = Bundle().apply {
                    putLong("exercise_id", ex.id)
                    putString("exercise_name", ex.name)
                }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }

            container.addView(cardView)
        }
    }

    private fun getUserId(): Long {
        val prefs = requireContext().getSharedPreferences("session", 0)
        return prefs.getLong("server_user_id", -1L)
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}