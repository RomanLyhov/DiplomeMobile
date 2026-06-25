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

class EditWorkoutFragment : Fragment() {

    private var workoutId: Long = -1L
    private var workoutName: String = ""
    private val exercises = mutableListOf<Exercise>()

    // Индекс упражнения, которое сейчас редактируется (-1 = никакое)
    private var editingIndex: Int = -1

    private lateinit var container: LinearLayout
    private lateinit var etWorkoutName: EditText
    private lateinit var etExerciseName: EditText
    private lateinit var etSets: EditText
    private lateinit var etReps: EditText
    private lateinit var etRest: EditText
    private lateinit var etWeight: EditText
    private var selectedExerciseId: Long? = null
    private lateinit var suggestionsContainer: LinearLayout
    private var searchJob: kotlinx.coroutines.Job? = null

    // Кнопка добавления упражнения — меняем её текст при редактировании
    private lateinit var btnAddExercise: Button

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
        etRest = view.findViewById(R.id.etRest)
        etWeight = view.findViewById(R.id.etWeight)
        suggestionsContainer = view.findViewById(R.id.suggestionsContainer)
        this.container = view.findViewById(R.id.containerExercises)
        btnAddExercise = view.findViewById(R.id.createWorkoutButton)

        etWorkoutName.setText(workoutName)

        setupExerciseSearch()

        btnAddExercise.setOnClickListener {
            if (editingIndex >= 0) {
                applyEdit()       // сохраняем правку существующего
            } else {
                addExercise()     // добавляем новое
            }
        }

        // Кнопка «Отмена» редактирования — появляется рядом с кнопкой добавления
        view.findViewById<Button?>(R.id.btnCancelEdit)?.setOnClickListener {
            cancelEdit()
        }

        view.findViewById<Button>(R.id.btnCompleteWorkout).apply {
            text = "Сохранить изменения"
            setOnClickListener { saveChanges() }
        }

        loadExistingExercises()

        return view
    }

    // ─────────────────────────────────────────────
    // Загрузка упражнений с сервера
    // ─────────────────────────────────────────────

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

    // ─────────────────────────────────────────────
    // Добавление нового упражнения
    // ─────────────────────────────────────────────

    private fun addExercise() {
        val name = etExerciseName.text.toString().trim()
        if (name.isEmpty()) { toast("Введите упражнение"); return }

        val sets = etSets.text.toString().toIntOrNull() ?: 3
        val reps = etReps.text.toString().toIntOrNull() ?: 10
        val weight = etWeight.text.toString().toFloatOrNull() ?: 0f
        val rest = etRest.text.toString().toIntOrNull() ?: 90  // ← ЧИТАЕМ ИЗ ПОЛЯ

        exercises.add(
            Exercise(
                id = selectedExerciseId ?: 0,
                name = name,
                sets = sets,
                reps = reps,
                weight = weight,
                rest = rest
            )
        )
        selectedExerciseId = null
        clearInputFields()
        render()
    }

    private fun startEditing(index: Int) {
        val ex = exercises[index]
        editingIndex = index

        etExerciseName.setText(ex.name)
        etSets.setText(ex.sets.toString())
        etReps.setText(ex.reps.toString())
        etWeight.setText(if (ex.weight > 0) ex.weight.toString() else "")
        etRest.setText(ex.rest.toString())
        selectedExerciseId = if (ex.id > 0) ex.id else null

        btnAddExercise.text = "Применить"

        // Прокручиваем к форме (если layout завёрнут в ScrollView)
        etExerciseName.requestFocus()

        render() // перерисовываем карточки, чтобы выделить редактируемую
    }

    /** Сохраняем изменения в объект списка и выходим из режима редактирования. */
    private fun applyEdit() {
        val name = etExerciseName.text.toString().trim()
        if (name.isEmpty()) { toast("Введите название упражнения"); return }

        exercises[editingIndex] = Exercise(
            id = selectedExerciseId ?: exercises[editingIndex].id,
            name = name,
            sets = etSets.text.toString().toIntOrNull() ?: 3,
            reps = etReps.text.toString().toIntOrNull() ?: 10,
            weight = etWeight.text.toString().toFloatOrNull() ?: 0f,
            rest = etRest.text.toString().toIntOrNull() ?: 90
        )

        editingIndex = -1
        selectedExerciseId = null
        btnAddExercise.text = "Добавить упражнение"
        clearInputFields()
        render()
    }

    /** Отменяем редактирование без сохранения. */
    private fun cancelEdit() {
        editingIndex = -1
        selectedExerciseId = null
        btnAddExercise.text = "Добавить упражнение"
        clearInputFields()
        render()
    }

    // ─────────────────────────────────────────────
    // Сохранение тренировки на сервер
    // ─────────────────────────────────────────────

    private fun saveChanges() {
        if (editingIndex >= 0) applyEdit()

        val name = etWorkoutName.text.toString().trim()
        if (name.isEmpty()) { toast("Введите название"); return }
        if (exercises.isEmpty()) { toast("Добавьте упражнения"); return }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = getUserId()

                // 1. Обновляем название тренировки
                ServerApiClient.apiService.updateWorkout(
                    workoutId,
                    WorkoutDto(id = workoutId, userId = 0, name = name)
                )

                // 2. Получаем ТЕКУЩИЕ упражнения с сервера (ДО изменения)
                val currentExercises = ApiManager.getExercises(workoutId)
                val currentWeightMap = currentExercises.associate { it.exerciseId to it.weight }
                val currentExerciseIds = currentExercises.map { it.exerciseId }.toSet()

                // 3. Удаляем старые упражнения
                ServerApiClient.apiService.deleteWorkoutExercises(workoutId)

                // 4. Записываем обновлённый список
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

                    // === ЛОГИКА СОХРАНЕНИЯ ПРОГРЕССА ===
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
                            Log.d("EDIT_WORKOUT", "Initial weight saved for ${ex.name}: ${ex.weight} кг")
                        } else {
                            // ЕСТЬ начальный вес → проверяем, изменился ли текущий вес
                            val previousWeight = currentWeightMap[ex.id]

                            // Если упражнение уже было в тренировке И вес изменился
                            if (currentExerciseIds.contains(ex.id) && previousWeight != null && ex.weight != previousWeight) {
                                // Вес изменился → сохраняем как новый прогресс
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
                                Log.d("EDIT_WORKOUT", "Progress saved for ${ex.name}: ${ex.weight} кг (was ${previousWeight})")
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    toast("Изменения сохранены")
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                Log.e("EDIT_WORKOUT", "save error", e)
                withContext(Dispatchers.Main) { toast("Ошибка сохранения: ${e.message}") }
            }
        }
    }

    // ─────────────────────────────────────────────
    // Поиск упражнений
    // ─────────────────────────────────────────────

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

    // ─────────────────────────────────────────────
    // Отрисовка карточек упражнений
    // ─────────────────────────────────────────────

    private fun render() {
        container.removeAllViews()
        val userId = getUserId()

        exercises.forEachIndexed { index, ex ->
            val v = layoutInflater.inflate(R.layout.exercise_card, container, false)

            v.findViewById<TextView>(R.id.tvExerciseName).text = ex.name
            v.findViewById<TextView>(R.id.tvSets).text = ex.sets.toString()
            v.findViewById<TextView>(R.id.tvReps).text = ex.reps.toString()
            v.findViewById<TextView>(R.id.tvWeight).text = ex.weight.toString()
            v.findViewById<TextView>(R.id.tvRest).text = "${ex.rest} сек"

            // === ПОКАЗЫВАЕМ НАЧАЛЬНЫЙ ВЕС ===
            val badge = v.findViewById<TextView>(R.id.tvInitialWeightBadge)

            lifecycleScope.launch {
                val initialWeight = ApiManager.getExerciseInitialWeight(userId, ex.id)
                if (initialWeight != null && initialWeight > 0) {
                    badge.text = "🏁 Начальный: ${initialWeight} кг"
                    badge.visibility = View.VISIBLE

                    // Показываем изменение
                    val diff = ex.weight - initialWeight
                    if (diff > 0) {
                        badge.append("  ↑ +${"%.1f".format(diff)} кг")
                        badge.setTextColor(resources.getColor(R.color.green, null))
                    } else if (diff < 0) {
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

            // Визуально выделяем редактируемую карточку
            if (index == editingIndex) {
                v.alpha = 1f
                v.setBackgroundResource(R.drawable.rounded_card_background_selected)
            } else {
                v.alpha = if (editingIndex >= 0) 0.5f else 1f
            }

            // Кнопка редактирования
            v.findViewById<TextView>(R.id.btnEditExercise).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    if (editingIndex == index) {
                        cancelEdit()
                    } else {
                        startEditing(index)
                    }
                }
            }

            // Кнопка удаления
            v.findViewById<TextView>(R.id.btnRemoveExercise).apply {
                visibility = View.VISIBLE
                isEnabled = editingIndex < 0 || editingIndex == index
                setOnClickListener {
                    if (editingIndex == index) cancelEdit()
                    exercises.removeAt(index)
                    render()
                }
            }

            container.addView(v)
        }
    }

    // ─────────────────────────────────────────────
    // Вспомогательные функции
    // ─────────────────────────────────────────────

    /** Получение ID пользователя из SharedPreferences */
    private fun getUserId(): Long {
        val prefs = requireContext().getSharedPreferences("session", 0)
        return prefs.getLong("server_user_id", -1L)
    }

    private fun clearInputFields() {
        etExerciseName.text.clear()
        etSets.setText("")
        etReps.setText("")
        etWeight.setText("")
        etRest.setText("")
        suggestionsContainer.visibility = View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}