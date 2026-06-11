package com.example.fitplan.ui

import android.R as AndroidR
import com.example.fitplan.R
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.Api.CalendarCreateDto
import com.example.fitplan.Models.Api.ServerApiClient
import com.example.fitplan.Models.Api.SyncManager
import com.example.fitplan.Models.Exercise
import com.example.fitplan.Models.Workout
import com.example.fitplan.Models.WorkoutDto
import com.example.fitplan.Models.WorkoutExerciseDto
import com.example.fitplan.ui.login.Login
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class WorkoutFragment : Fragment(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext get() = Dispatchers.Main + job

    private var userId: Long = -1L
    private lateinit var containerWorkouts: LinearLayout
    private lateinit var emptyStateCard: LinearLayout
    private lateinit var createWorkoutButton: Button
    private lateinit var btnRefresh: Button

    private val expandedWorkouts = mutableSetOf<Long>()
    private val workoutExercisesCache = mutableMapOf<Long, List<Exercise>>()

    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_workout, container, false)

        containerWorkouts = view.findViewById(R.id.containerWorkouts)
        emptyStateCard = view.findViewById(R.id.emptyStateCard)
        createWorkoutButton = view.findViewById(R.id.createWorkoutButton)
        btnRefresh = view.findViewById(R.id.btnRefresh)

        // Кнопка истории
        val btnHistory = view.findViewById<TextView>(R.id.btnHistory)

        createWorkoutButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CreateWorkoutFragment())
                .addToBackStack(null)
                .commit()
        }

        btnRefresh.setOnClickListener {
            expandedWorkouts.clear()
            workoutExercisesCache.clear()
            loadWorkouts()
        }

        // Обработка нажатия на историю
        btnHistory.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WorkoutHistoryFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userId = requireContext()
            .getSharedPreferences("session", android.content.Context.MODE_PRIVATE)
            .getLong("server_user_id", -1L)

        if (userId == -1L) {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, Login())
                .commit()
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

    // ================= LOAD WORKOUTS FROM SERVER =================

    private fun loadWorkouts() {

        if (userId == -1L || isLoading) return
        isLoading = true

        launch {
            try {

                val data = withContext(Dispatchers.IO) {
                    ApiManager.getWorkoutsFullClient(userId)
                }

                Log.d("WORKOUTS", "SIZE = ${data.size}")

                withContext(Dispatchers.Main) {

                    containerWorkouts.removeAllViews()

                    if (data.isEmpty()) {
                        emptyStateCard.visibility = View.VISIBLE
                        return@withContext
                    }

                    emptyStateCard.visibility = View.GONE

                    data.forEach { (workout, exercises) ->
                        addWorkoutCard(workout, exercises)
                    }
                }

            } catch (e: Exception) {
                Log.e("WORKOUT", "load error", e)
            } finally {
                isLoading = false
            }
        }
    }

    // ================= CARD =================

    private fun addWorkoutCard(
        workout: WorkoutDto,
        exercises: List<WorkoutExerciseDto>
    ) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val card = layoutInflater.inflate(R.layout.workout_card, container, false)

        val title = card.findViewById<TextView>(R.id.tvWorkoutName)
        val count = card.findViewById<TextView>(R.id.tvExerciseCount)
        val dateView = card.findViewById<TextView>(R.id.tvDate)
        val actions = card.findViewById<LinearLayout>(R.id.actionsContainer)
        val btnStart = card.findViewById<Button>(R.id.btnStartWorkout)
        val btnEdit = card.findViewById<Button>(R.id.btnEditWorkout)
        val btnDelete = card.findViewById<Button>(R.id.btnDelete)

        val exercisesContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        title.text = workout.name
        count.text = "${exercises.size} упражнений"

        // Дата
        dateView.text = workout.createdAt?.let { formatDate(it) } ?: ""

        // Раскрытие по клику на карточку
        card.findViewById<LinearLayout>(R.id.cardRoot).setOnClickListener {
            val expanded = exercisesContainer.visibility == View.VISIBLE
            if (expanded) {
                exercisesContainer.visibility = View.GONE
            } else {
                exercisesContainer.visibility = View.VISIBLE
                exercisesContainer.removeAllViews()
                exercises.forEach { ex ->
                    val v = layoutInflater.inflate(R.layout.exercise_card, exercisesContainer, false)
                    v.findViewById<TextView>(R.id.tvExerciseName).text =
                        if (ex.name.isNotEmpty()) ex.name else "Без названия"
                    v.findViewById<TextView>(R.id.tvSets).text = ex.sets.toString()
                    v.findViewById<TextView>(R.id.tvReps).text = ex.reps.toString()
                    v.findViewById<TextView>(R.id.tvWeight).text = "${ex.weight} кг"
                    v.findViewById<TextView>(R.id.tvRest).text = "${ex.rest} сек"
                    exercisesContainer.addView(v)
                }
            }
        }

        btnStart.setOnClickListener {

            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    val selectedCal = java.util.Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val timestamp = selectedCal.timeInMillis

                    launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                ServerApiClient.apiService.addToCalendar(
                                    CalendarCreateDto(
                                        userId = userId,
                                        workoutId = workout.id,
                                        scheduledDate = timestamp
                                    )
                                )
                            }
                            if (response.isSuccessful) {
                                val dateStr = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("ru"))
                                    .format(selectedCal.time)
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragment_container,
                                        WorkoutProcessFragment.newInstance(workout.id, workout.name))
                                    .addToBackStack(null)
                                    .commit()
                                Toast.makeText(
                                    requireContext(),
                                    "Тренировка '${workout.name}' запланирована на $dateStr",
                                    Toast.LENGTH_SHORT
                                ).show()
                                // Планируем уведомление
                                scheduleNotification(workout.name, timestamp)
                            } else {
                                Toast.makeText(requireContext(), "Ошибка планирования", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("CALENDAR", "error", e)
                        }
                    }
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()

        }


        // Изменить
        btnEdit.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container,
                    EditWorkoutFragment.newInstance(workout.id, workout.name))
                .addToBackStack(null)
                .commit()
        }

        // Удалить
        btnDelete.setOnClickListener {
            Log.d("DELETE_DEBUG", "workout.id = ${workout.id}, name = ${workout.name}")
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Удалить тренировку?")
                .setMessage("'${workout.name}' будет удалена безвозвратно")
                .setPositiveButton("Удалить") { _, _ ->
                    launch {
                        val success = withContext(Dispatchers.IO) {
                            deleteWorkoutFromServer(workout.id)
                        }
                        if (success) {
                            containerWorkouts.removeView(container)
                            Toast.makeText(requireContext(), "Удалено", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        container.addView(card)
        container.addView(exercisesContainer)
        containerWorkouts.addView(container)
    }

    private fun formatDate(raw: String): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("d MMM", java.util.Locale("ru"))
            val date = inputFormat.parse(raw)
            if (date != null) outputFormat.format(date) else ""
        } catch (e: Exception) {
            try {
                val inputFormat2 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
                val outputFormat = java.text.SimpleDateFormat("d MMM", java.util.Locale("ru"))
                val date = inputFormat2.parse(raw)
                if (date != null) outputFormat.format(date) else ""
            } catch (e2: Exception) {
                ""
            }
        }
    }

    private fun scheduleNotification(workoutName: String, timestampMs: Long) {
        val context = requireContext()

        // Создаём канал уведомлений (нужно для Android 8+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "workout_channel",
                "Тренировки",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Напоминания о тренировках" }
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Считаем задержку
        val delay = timestampMs - System.currentTimeMillis()
        if (delay <= 0) return

        // Запускаем корутину с задержкой
        launch {
            kotlinx.coroutines.delay(delay)
            if (!isAdded) return@launch
            val notification = androidx.core.app.NotificationCompat.Builder(context, "workout_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Тренировка сегодня!")
                .setContentText("Запланировано: $workoutName")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            manager.notify(workoutName.hashCode(), notification)
        }
    }

    private suspend fun deleteWorkoutFromServer(workoutId: Long): Boolean {
        return try {

            Log.d("DELETE_API", "Deleting workoutId=$workoutId")

            val response =
                ServerApiClient.apiService.deleteWorkout(workoutId)

            Log.d("DELETE_API", "code=${response.code()}")
            Log.d("DELETE_API", "body=${response.body()}")
            Log.d(
                "DELETE_API",
                "error=${response.errorBody()?.string()}"
            )

            response.isSuccessful

        } catch (e: Exception) {

            Log.e("DELETE_API", "delete crash", e)
            false
        }
    }

    // ================= TOGGLE + LOAD EXERCISES =================

    private fun toggleWorkout(
        workoutId: Long,
        title: TextView,
        actions: LinearLayout,
        container: LinearLayout
    ) {

        if (expandedWorkouts.contains(workoutId)) {

            expandedWorkouts.remove(workoutId)
            container.visibility = View.GONE
            container.removeAllViews()
            actions.visibility = View.GONE
            title.text = "▶ тренировка"

        } else {

            expandedWorkouts.add(workoutId)
            container.visibility = View.VISIBLE
            actions.visibility = View.VISIBLE
            title.text = "▼ тренировка"

            loadExercises(workoutId, container)
        }
    }

    private fun loadExercises(workoutId: Long, container: LinearLayout) {

        launch {

            val exercises = withContext(Dispatchers.IO) {
                ApiManager.getExercises(workoutId)
            }

            container.removeAllViews()

            if (exercises.isEmpty()) {
                val tv = TextView(requireContext())
                tv.text = "Нет упражнений"
                container.addView(tv)
                return@launch
            }

            exercises.forEach { ex ->

                val v = layoutInflater.inflate(R.layout.exercise_card, container, false)

                val nameView = v.findViewById<TextView>(R.id.tvExerciseName)
                val setsView = v.findViewById<TextView>(R.id.tvSets)
                val repsView = v.findViewById<TextView>(R.id.tvReps)
                val weightView = v.findViewById<TextView>(R.id.tvWeight)
                val restView = v.findViewById<TextView>(R.id.tvRest)

                // сразу ставим заглушку
                nameView.text = if (ex.name.isNotEmpty()) ex.name else "Без названия"

                setsView.text = ex.sets.toString()
                repsView.text = ex.reps.toString()
                weightView.text = "${ex.weight} кг"
                restView.text = "${ex.rest} сек"

                container.addView(v)
            }
        }
    }

    // ================= DELETE (если нужно потом серверный) =================

    private fun deleteWorkout(workoutId: Long) {
        Toast.makeText(requireContext(), "Нужен endpoint удаления", Toast.LENGTH_SHORT).show()
    }
}