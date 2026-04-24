package com.example.fitplan.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Exercise
import com.example.fitplan.R
import com.example.fitplan.ui.login.Login
import java.util.concurrent.Executors

// Фрагмент для создания и редактирования тренировок
class CreateWorkoutFragment : Fragment() {

    // Переменные для работы с базой данных и состояниями
    private lateinit var db: DatabaseHelper
    private var userId = -1L
    private var workoutId = -1L
    private var isEditMode = false
    private var editingExerciseIndex: Int? = null
    private val exercises = mutableListOf<Exercise>()

    // Ссылки на элементы интерфейса
    private lateinit var containerExercises: LinearLayout
    private lateinit var tvExercisesCount: TextView
    private lateinit var btnAddExercise: TextView
    private lateinit var etWorkoutName: EditText
    private lateinit var etExerciseName: EditText
    private lateinit var etWeight: EditText
    private lateinit var etRest: EditText
    private lateinit var tvSets: TextView
    private lateinit var tvReps: TextView

    // Исполнители для работы в разных потоках
    private val handler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    // Инициализация фрагмента
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = DatabaseHelper(requireContext())

        val prefs = requireContext().getSharedPreferences("session", android.content.Context.MODE_PRIVATE)
        userId = prefs.getLong("user_id", -1L)

        Log.d("CreateWorkoutFragment", "User ID from SharedPreferences: $userId")

        workoutId = arguments?.getLong("WORKOUT_ID", -1) ?: -1
        isEditMode = workoutId != -1L

        Log.d("CreateWorkoutFragment", "isEditMode: $isEditMode, workoutId: $workoutId")
    }

    // Очистка ресурсов при уничтожении фрагмента
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        ioExecutor.shutdown()
        db.close()
    }

    // Создание интерфейса фрагмента
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_create_workout, container, false)

        // Сохраняем ссылки на View
        etWorkoutName = view.findViewById(R.id.etWorkoutName)
        etExerciseName = view.findViewById(R.id.etExerciseName)
        etWeight = view.findViewById(R.id.etWeight)
        etRest = view.findViewById(R.id.etRestTime)
        tvSets = view.findViewById(R.id.tvSetsCount)
        tvReps = view.findViewById(R.id.tvRepsCount)
        tvExercisesCount = view.findViewById(R.id.tvExercisesCount)
        containerExercises = view.findViewById(R.id.containerExercises)
        btnAddExercise = view.findViewById(R.id.createWorkoutButton)
        val btnSaveWorkout = view.findViewById<TextView>(R.id.btnCompleteWorkout)

        // Добавляем обработчик кнопки "Назад"
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Обновляем видимость контейнера
        updateExercisesContainerVisibility()

        if (isEditMode) {
            loadExistingWorkout()
        }

        setupNumberButtons(view)
        setupAddExerciseButton()
        setupSaveWorkoutButton(btnSaveWorkout)

        return view
    }

    // Загрузка существующей тренировки для редактирования
    private fun loadExistingWorkout() {
        ioExecutor.execute {
            try {
                Log.d("CreateWorkoutFragment", "Loading workout with ID: $workoutId")
                val workout = db.getWorkoutById(workoutId)
                val list = db.getExercises(workoutId)

                Log.d("CreateWorkoutFragment", "Workout loaded: ${workout != null}")
                Log.d("CreateWorkoutFragment", "Exercises loaded: ${list.size}")

                handler.post {
                    if (workout == null) {
                        Toast.makeText(requireContext(), "Тренировка не найдена", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                        return@post
                    }

                    etWorkoutName.setText(workout.name)
                    exercises.clear()
                    exercises.addAll(list)
                    refreshExerciseList()
                    btnAddExercise.text = "Сохранить изменения"
                    tvExercisesCount.text = "${exercises.size} упражнений"
                    Toast.makeText(requireContext(), "Загружено ${list.size} упражнений", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CREATE_CRASH", "edit load error", e)
                handler.post {
                    Toast.makeText(requireContext(), "Ошибка загрузки тренировки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Настройка кнопок для изменения количества подходов и повторений
    private fun setupNumberButtons(view: View) {
        // Простые обработчики без дебаунса для скорости
        view.findViewById<TextView>(R.id.btnIncreaseSets).setOnClickListener {
            val current = tvSets.text.toString().toIntOrNull() ?: 3
            tvSets.text = (current + 1).toString()
        }

        view.findViewById<TextView>(R.id.btnDecreaseSets).setOnClickListener {
            val current = tvSets.text.toString().toIntOrNull() ?: 3
            if (current > 1) tvSets.text = (current - 1).toString()
        }

        view.findViewById<TextView>(R.id.btnIncreaseReps).setOnClickListener {
            val current = tvReps.text.toString().toIntOrNull() ?: 10
            tvReps.text = (current + 1).toString()
        }

        view.findViewById<TextView>(R.id.btnDecreaseReps).setOnClickListener {
            val current = tvReps.text.toString().toIntOrNull() ?: 10
            if (current > 1) tvReps.text = (current - 1).toString()
        }
    }

    // Настройка кнопки добавления упражнения
    private fun setupAddExerciseButton() {
        btnAddExercise.setOnClickListener {
            val name = etExerciseName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Введите название упражнения", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val exercise = Exercise(
                id = 0,
                workoutId = workoutId,
                name = name,
                sets = tvSets.text.toString().toIntOrNull() ?: 3,
                reps = tvReps.text.toString().toIntOrNull() ?: 10,
                weight = etWeight.text.toString().toIntOrNull() ?: 0,
                rest = etRest.text.toString().toIntOrNull() ?: 90
            )

            if (editingExerciseIndex == null) {
                // Добавляем новое упражнение
                exercises.add(exercise)
                addExerciseCard(exercise, exercises.size - 1)
            } else {
                // Редактируем существующее
                exercises[editingExerciseIndex!!] = exercise
                updateExerciseCard(editingExerciseIndex!!, exercise)
                editingExerciseIndex = null
                btnAddExercise.text = "Добавить упражнение"
            }

            // Обновляем интерфейс
            tvExercisesCount.text = "${exercises.size} упражнений"
            updateExercisesContainerVisibility()

            // Сбрасываем поля
            etExerciseName.text.clear()
            etWeight.setText("0")
            etRest.setText("90")
            tvSets.text = "3"
            tvReps.text = "10"
        }
    }

    // Настройка кнопки сохранения тренировки
    private fun setupSaveWorkoutButton(btnSaveWorkout: TextView) {
        btnSaveWorkout.setOnClickListener {
            if (userId == -1L) {
                Toast.makeText(requireContext(), "Пользователь не найден", Toast.LENGTH_SHORT).show()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, Login())
                    .commit()
                return@setOnClickListener
            }

            val workoutName = etWorkoutName.text.toString().trim()
            if (workoutName.isEmpty()) {
                Toast.makeText(requireContext(), "Введите название тренировки", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (exercises.isEmpty()) {
                Toast.makeText(requireContext(), "Добавьте хотя бы одно упражнение", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Показываем прогресс
            Toast.makeText(requireContext(), "Сохранение...", Toast.LENGTH_SHORT).show()

            ioExecutor.execute {
                try {
                    if (isEditMode) {
                        // Обновляем существующую тренировку
                        val updated = db.updateWorkout(workoutId, workoutName)
                        Log.d("CreateWorkoutFragment", "Updated workout: $updated")

                        db.deleteExercisesByWorkout(workoutId)
                        Log.d("CreateWorkoutFragment", "Deleted old exercises")

                        exercises.forEach { exercise ->
                            db.addExerciseToWorkout(workoutId, exercise)
                        }

                        handler.post {
                            Toast.makeText(requireContext(), "Тренировка сохранена", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        }
                    } else {
                        // Создаем новую тренировку
                        val newWorkoutId = db.addWorkout(userId, workoutName)
                        Log.d("CreateWorkoutFragment", "Created new workout ID: $newWorkoutId")

                        if (newWorkoutId == -1L) {
                            handler.post {
                                Toast.makeText(requireContext(), "Ошибка создания тренировки", Toast.LENGTH_SHORT).show()
                            }
                            return@execute
                        }

                        exercises.forEach { exercise ->
                            db.addExerciseToWorkout(newWorkoutId, exercise)
                        }

                        handler.post {
                            Toast.makeText(requireContext(), "Тренировка создана", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CreateWorkoutFragment", "Error saving workout", e)
                    handler.post {
                        Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Полное обновление списка упражнений
    private fun refreshExerciseList() {
        containerExercises.removeAllViews()
        exercises.forEachIndexed { index, exercise ->
            addExerciseCard(exercise, index)
        }
        updateExercisesContainerVisibility()
    }

    // Добавление карточки упражнения в список
    private fun addExerciseCard(exercise: Exercise, index: Int) {
        val card = layoutInflater.inflate(R.layout.exercise_card, containerExercises, false)

        // Заполняем данные
        card.findViewById<TextView>(R.id.tvExerciseName).text = exercise.name
        card.findViewById<TextView>(R.id.tvSets).text = exercise.sets.toString()
        card.findViewById<TextView>(R.id.tvReps).text = exercise.reps.toString()
        card.findViewById<TextView>(R.id.tvWeight).text = "${exercise.weight} кг"
        card.findViewById<TextView>(R.id.tvRest).text = "${exercise.rest} сек"

        // Показываем кнопку удаления
        val btnRemove = card.findViewById<TextView>(R.id.btnRemoveExercise)
        btnRemove.visibility = View.VISIBLE
        btnRemove.setOnClickListener {
            exercises.removeAt(index)
            refreshExerciseList()
            tvExercisesCount.text = "${exercises.size} упражнений"
        }

        // Клик для редактирования
        card.setOnClickListener {
            editingExerciseIndex = index
            etExerciseName.setText(exercise.name)
            tvSets.text = exercise.sets.toString()
            tvReps.text = exercise.reps.toString()
            etWeight.setText(exercise.weight.toString())
            etRest.setText(exercise.rest.toString())
            btnAddExercise.text = "Сохранить изменения"
        }

        containerExercises.addView(card)
    }

    // Обновление карточки упражнения
    private fun updateExerciseCard(index: Int, exercise: Exercise) {
        if (index < containerExercises.childCount) {
            val card = containerExercises.getChildAt(index)
            card.findViewById<TextView>(R.id.tvExerciseName).text = exercise.name
            card.findViewById<TextView>(R.id.tvSets).text = exercise.sets.toString()
            card.findViewById<TextView>(R.id.tvReps).text = exercise.reps.toString()
            card.findViewById<TextView>(R.id.tvWeight).text = "${exercise.weight} кг"
            card.findViewById<TextView>(R.id.tvRest).text = "${exercise.rest} сек"
        }
    }

    // Обновление видимости контейнера с упражнениями
    private fun updateExercisesContainerVisibility() {
        if (exercises.isNotEmpty()) {
            containerExercises.visibility = View.VISIBLE
        } else {
            containerExercises.visibility = View.GONE
        }
    }
}