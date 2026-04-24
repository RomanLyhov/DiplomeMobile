package com.example.fitplan.ui

import android.R as AndroidR
import com.example.fitplan.R
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Exercise
import com.example.fitplan.Models.Workout
import com.example.fitplan.ui.login.Login
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class WorkoutFragment : Fragment(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var db: DatabaseHelper
    private var userId: Long = -1L
    private lateinit var containerWorkouts: LinearLayout
    private lateinit var emptyStateCard: LinearLayout
    private lateinit var createWorkoutButton: Button
    private lateinit var btnRefresh: Button
    private val expandedWorkouts = mutableSetOf<Long>()
    private val workoutContainers = mutableMapOf<Long, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseHelper(requireContext())
        job = Job()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_workout, container, false)

        // Инициализация элементов фрагмента
        containerWorkouts = view.findViewById(R.id.containerWorkouts)
        emptyStateCard = view.findViewById(R.id.emptyStateCard)
        createWorkoutButton = view.findViewById(R.id.createWorkoutButton)
        btnRefresh = view.findViewById(R.id.btnRefresh)

        createWorkoutButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CreateWorkoutFragment())
                .addToBackStack(null)
                .commit()
        }

        btnRefresh.setOnClickListener {
            expandedWorkouts.clear()
            workoutContainers.clear()
            loadWorkouts()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = requireContext()
            .getSharedPreferences("session", android.content.Context.MODE_PRIVATE)
            .getLong("user_id", -1L)

        Log.d("WORKOUT", "onViewCreated → UserId = $userId")

        if (userId == -1L) {
            Log.e("WORKOUT", "USER_ID = -1 → переход на Login")
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, Login())
                .commit()
            return
        }
    }

    override fun onResume() {
        super.onResume()
        loadWorkouts()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun loadWorkouts() {
        if (userId == -1L) return

        launch {
            try {
                val workouts = withContext(Dispatchers.IO) {
                    db.getWorkoutsByUser(userId)
                }

                // Очищаем UI
                containerWorkouts.removeAllViews()
                workoutContainers.clear()

                if (workouts.isEmpty()) {
                    emptyStateCard.visibility = View.VISIBLE
                } else {
                    emptyStateCard.visibility = View.GONE
                    workouts.forEach { addWorkoutCard(it) }
                }
            } catch (e: Exception) {
                Log.e("WORKOUT_CRASH", "Ошибка загрузки тренировок", e)
                emptyStateCard.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Ошибка загрузки тренировок", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addWorkoutCard(workout: Workout) {
        try {
            Log.d("WORKOUT", "Добавление карточки тренировки: ${workout.name}")

            // Создаем основной контейнер
            val workoutContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
            }

            // Инфлейтим макет карточки
            val workoutCard = layoutInflater.inflate(R.layout.workout_card, workoutContainer, false)

            // Находим элементы UI
            val cardRoot = workoutCard.findViewById<LinearLayout>(R.id.cardRoot)
            val tvWorkoutName = workoutCard.findViewById<TextView>(R.id.tvWorkoutName)
            val tvExerciseCount = workoutCard.findViewById<TextView>(R.id.tvExerciseCount)
            val actionsContainer = workoutCard.findViewById<LinearLayout>(R.id.actionsContainer)

            // НАХОДИМ ВСЕ КНОПКИ (теперь включая кнопку удаления)
            val btnStart = workoutCard.findViewById<Button>(R.id.btnStartWorkout)
            val btnEdit = workoutCard.findViewById<Button>(R.id.btnEditWorkout)
            val btnDelete = workoutCard.findViewById<Button>(R.id.btnDelete)

            // Создаем контейнер для упражнений
            val exercisesContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                visibility = if (expandedWorkouts.contains(workout.id)) View.VISIBLE else View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 0, 16, 16)
                }
            }

            // Сохраняем ссылку на контейнер
            workoutContainers[workout.id] = workoutContainer

            launch {
                try {
                    val exercises = withContext(Dispatchers.IO) {
                        db.getExercises(workout.id)
                    }

                    // Обновляем UI в основном потоке
                    withContext(Dispatchers.Main) {
                        tvExerciseCount.text = "${exercises.size} упражнений"
                        tvWorkoutName.text = if (expandedWorkouts.contains(workout.id))
                            "▼ ${workout.name}" else "▶ ${workout.name}"

                        actionsContainer.visibility =
                            if (expandedWorkouts.contains(workout.id)) View.VISIBLE else View.GONE

                        // Обработчик клика на карточку
                        cardRoot.setOnClickListener {
                            toggleWorkout(workout, tvWorkoutName, actionsContainer,
                                exercisesContainer, exercises)
                        }

                        // Обработчик долгого нажатия для удаления
                        cardRoot.setOnLongClickListener {
                            showDeleteConfirmationDialog(workout, workoutContainer)
                            true
                        }

                        // Если тренировка развернута, добавляем упражнения
                        if (expandedWorkouts.contains(workout.id)) {
                            addExercisesToContainer(exercises, exercisesContainer)
                        }

                        // Обработчики других кнопок
                        btnStart.setOnClickListener {
                            val bundle = Bundle().apply { putLong("WORKOUT_ID", workout.id) }
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, WorkoutProcessFragment().apply { arguments = bundle })
                                .addToBackStack(null)
                                .commit()
                        }

                        btnEdit.setOnClickListener {
                            val bundle = Bundle().apply { putLong("WORKOUT_ID", workout.id) }
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragment_container, CreateWorkoutFragment().apply { arguments = bundle })
                                .addToBackStack(null)
                                .commit()
                        }

                        // Обработчик кнопки удаления
                        btnDelete.setOnClickListener {
                            showDeleteConfirmationDialog(workout, workoutContainer)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WORKOUT", "Ошибка загрузки упражнений для тренировки ${workout.id}", e)
                }
            }

            // Добавляем карточку в контейнеры
            workoutContainer.addView(workoutCard)
            workoutContainer.addView(exercisesContainer)
            containerWorkouts.addView(workoutContainer)

        } catch (e: Exception) {
            Log.e("WORKOUT", "Ошибка при создании карточки тренировки", e)
        }
    }

    private fun toggleWorkout(
        workout: Workout,
        tvTitle: TextView,
        actions: LinearLayout,
        exercisesContainer: LinearLayout,
        exercises: List<Exercise>
    ) {
        if (expandedWorkouts.contains(workout.id)) {
            // Сворачиваем
            expandedWorkouts.remove(workout.id)
            tvTitle.text = "▶ ${workout.name}"
            actions.visibility = View.GONE
            exercisesContainer.visibility = View.GONE
            exercisesContainer.removeAllViews()
        } else {
            // Разворачиваем
            expandedWorkouts.add(workout.id)
            tvTitle.text = "▼ ${workout.name}"
            actions.visibility = View.VISIBLE
            exercisesContainer.visibility = View.VISIBLE
            addExercisesToContainer(exercises, exercisesContainer)
        }
    }

    private fun addExercisesToContainer(exercises: List<Exercise>, container: LinearLayout) {
        container.removeAllViews()

        if (exercises.isEmpty()) {
            val textView = TextView(requireContext()).apply {
                text = "Нет упражнений"
                setTextColor(resources.getColor(R.color.text_secondary, null))
                textSize = 14f
                setPadding(16, 8, 16, 8)
            }
            container.addView(textView)
            return
        }

        exercises.forEach { exercise ->
            val card = layoutInflater.inflate(R.layout.exercise_card, container, false)
            card.findViewById<TextView>(R.id.tvExerciseName).text = exercise.name
            card.findViewById<TextView>(R.id.tvSets).text = exercise.sets.toString()
            card.findViewById<TextView>(R.id.tvReps).text = exercise.reps.toString()
            card.findViewById<TextView>(R.id.tvWeight).text = "${exercise.weight} кг"
            card.findViewById<TextView>(R.id.tvRest)?.text = "${exercise.rest} сек"
            container.addView(card)
        }
    }

    // ====================== МЕТОДЫ ДЛЯ УДАЛЕНИЯ ТРЕНИРОВКИ ======================

    private fun showDeleteConfirmationDialog(workout: Workout, workoutContainer: View) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить тренировку")
            .setMessage("Вы уверены, что хотите удалить тренировку \"${workout.name}\"?\n\nВсе упражнения в ней также будут удалены.")
            .setPositiveButton("Удалить") { dialog, which ->
                deleteWorkout(workout, workoutContainer)
            }
            .setNegativeButton("Отмена", null)
            .setCancelable(true)
            .show()
    }

    private fun deleteWorkout(workout: Workout, workoutContainer: View) {
        launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    db.deleteWorkout(workout.id)
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        // Удаляем карточку с анимацией
                        workoutContainer.animate()
                            .alpha(0f)
                            .scaleX(0.9f)
                            .scaleY(0.9f)
                            .setDuration(250)
                            .withEndAction {
                                containerWorkouts.removeView(workoutContainer)
                                workoutContainers.remove(workout.id)
                                expandedWorkouts.remove(workout.id)

                                // Проверяем, нужно ли показать пустое состояние
                                if (containerWorkouts.childCount == 0) {
                                    emptyStateCard.visibility = View.VISIBLE
                                }
                            }
                            .start()

                        Toast.makeText(
                            requireContext(),
                            "Тренировка \"${workout.name}\" удалена",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.d("WORKOUT", "Тренировка ${workout.name} удалена")
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Не удалось удалить тренировку",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("WORKOUT", "Ошибка при удалении тренировки", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка при удалении: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}