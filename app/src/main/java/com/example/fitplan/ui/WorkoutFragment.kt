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
import java.util.Calendar
import kotlin.coroutines.CoroutineContext

class WorkoutFragment : Fragment(), CoroutineScope {

    private lateinit var job: Job
    override val coroutineContext get() = Dispatchers.Main + job

    private var userId: Long = -1L
    private lateinit var containerWorkouts: LinearLayout
    private lateinit var emptyStateCard: LinearLayout
    private lateinit var createWorkoutButton: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnPlanWorkout: TextView

    private val expandedWorkouts = mutableSetOf<Long>()
    private val workoutExercisesCache = mutableMapOf<Long, List<Exercise>>()

    private var showRecommended: Boolean = true
    private lateinit var recommendedSection: LinearLayout
    private lateinit var recommendedContainer: LinearLayout
    private lateinit var btnToggleRecommended: TextView

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
        btnPlanWorkout = view.findViewById(R.id.btnPlanWorkout)

        // Кнопка истории
        val btnHistory = view.findViewById<TextView>(R.id.btnHistory)

        createWorkoutButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CreateWorkoutFragment())
                .addToBackStack(null)
                .commit()
        }

        btnPlanWorkout.setOnClickListener {

            val workouts = containerWorkouts.childCount

            if (workouts == 0) {
                Toast.makeText(
                    requireContext(),
                    "Сначала создайте тренировку",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            showWorkoutPickerDialog()
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
        val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
        showRecommended = prefs.getBoolean("show_recommended", true)
    }

    private fun showWorkoutPickerDialog() {

        launch {

            try {

                val workouts = withContext(Dispatchers.IO) {
                    ApiManager.getWorkouts(userId) ?: emptyList()
                }

                if (workouts.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Нет тренировок",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val workoutNames = workouts.map { it.name }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("Выберите тренировку")
                    .setItems(workoutNames) { _, which ->

                        val selectedWorkout = workouts[which]

                        DateTimePickerDialog(requireContext()) { timestamp ->

                            launch {

                                try {

                                    val response =
                                        withContext(Dispatchers.IO) {
                                            ServerApiClient.apiService.addToCalendar(
                                                CalendarCreateDto(
                                                    userId = userId,
                                                    workoutId = selectedWorkout.id,
                                                    scheduledDate = timestamp
                                                )
                                            )
                                        }

                                    if (response.isSuccessful) {

                                        val dateFormat =
                                            java.text.SimpleDateFormat(
                                                "d MMM yyyy, HH:mm",
                                                java.util.Locale("ru")
                                            )

                                        val dateStr =
                                            dateFormat.format(
                                                java.util.Date(timestamp)
                                            )

                                        Toast.makeText(
                                            requireContext(),
                                            "'${selectedWorkout.name}' запланирована на $dateStr",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        scheduleNotifications(
                                            selectedWorkout.name,
                                            timestamp
                                        )

                                    } else {
                                        Toast.makeText(
                                            requireContext(),
                                            "Ошибка планирования",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                } catch (e: Exception) {

                                    Toast.makeText(
                                        requireContext(),
                                        "Ошибка: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }.show()
                    }
                    .show()

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Ошибка загрузки тренировок",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecommended()
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

            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    WorkoutProcessFragment.newInstance(
                        workout.id,
                        workout.name
                    )
                )
                .addToBackStack(null)
                .commit()
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

                            // Удаляем карточку сразу из UI
                            containerWorkouts.removeView(container)

                            Toast.makeText(
                                requireContext(),
                                "Удалено",
                                Toast.LENGTH_SHORT
                            ).show()
                            refreshAll()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Ошибка удаления",
                                Toast.LENGTH_SHORT
                            ).show()
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

    private fun refreshAll() {

        launch {

            try {

                // Сначала мои тренировки
                val workouts = withContext(Dispatchers.IO) {
                    ApiManager.getWorkoutsFullClient(userId)
                }

                if (!isAdded) return@launch

                containerWorkouts.removeAllViews()

                if (workouts.isEmpty()) {
                    emptyStateCard.visibility = View.VISIBLE
                } else {
                    emptyStateCard.visibility = View.GONE

                    workouts.forEach { (workout, exercises) ->
                        addWorkoutCard(workout, exercises)
                    }
                }

                // 🔥 После этого грузим рекомендации
                delay(300)

                val recommended = withContext(Dispatchers.IO) {
                    ApiManager.getRecommendedWorkouts(userId)
                }

                if (!isAdded) return@launch

                recommendedContainer.removeAllViews()

                if (recommended.isEmpty()) {
                    recommendedSection.visibility = View.GONE
                    return@launch
                }

                recommendedSection.visibility = View.VISIBLE

                recommended.forEach { workout ->

                    val card = layoutInflater.inflate(
                        R.layout.workout_card,
                        recommendedContainer,
                        false
                    )

                    card.findViewById<TextView>(R.id.tvWorkoutName)
                        .text = workout.name

                    card.findViewById<TextView>(R.id.tvExerciseCount)
                        .text = "${workout.exercises.size} упражнений"

                    card.findViewById<TextView>(R.id.tvDate)
                        .text = "⭐ Рекомендовано"

                    card.findViewById<Button>(R.id.btnEditWorkout)
                        .visibility = View.GONE

                    card.findViewById<Button>(R.id.btnDelete)
                        .visibility = View.GONE

                    recommendedContainer.addView(card)
                }

            } catch (e: Exception) {
                Log.e("REFRESH_ALL", "error", e)
            }
        }
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

    private fun scheduleNotifications(workoutName: String, timestampMs: Long) {
        Log.d("NOTIFICATION", "=== SCHEDULING NOTIFICATIONS START ===")
        Log.d("NOTIFICATION", "Workout: $workoutName")
        Log.d("NOTIFICATION", "Workout time: ${java.util.Date(timestampMs)}")
        Log.d("NOTIFICATION", "Current time: ${java.util.Date(System.currentTimeMillis())}")

        val context = requireContext()
        val now = System.currentTimeMillis()

        // 1. Уведомление за 2 часа до тренировки
        val twoHoursBefore = timestampMs - (2 * 60 * 60 * 1000)
        if (twoHoursBefore > now) {
            NotificationScheduler.scheduleNotification(
                context,
                workoutName,
                twoHoursBefore,
                "Через 2 часа тренировка!",
                "Не забудьте подготовиться к тренировке '$workoutName'"
            )
        }

        // 2. Уведомление за 30 минут до тренировки
        val halfHourBefore = timestampMs - (30 * 60 * 1000)
        if (halfHourBefore > now) {
            NotificationScheduler.scheduleNotification(
                context,
                workoutName,
                halfHourBefore,
                "Скоро тренировка!",
                "Тренировка '$workoutName' начнётся через 30 минут"
            )
        }

        // 3. Уведомление в день тренировки (в 8:00 утра)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val morningNotification = calendar.timeInMillis
        if (morningNotification > now && morningNotification < timestampMs) {
            NotificationScheduler.scheduleNotification(
                context,
                workoutName,
                morningNotification,
                "Сегодня тренировка!",
                "У вас запланирована тренировка '$workoutName' на сегодня"
            )
        }

        // 4. Уведомление за 5 минут до тренировки
        val fiveMinutesBefore = timestampMs - (5 * 60 * 1000)
        if (fiveMinutesBefore > now) {
            NotificationScheduler.scheduleNotification(
                context,
                workoutName,
                fiveMinutesBefore,
                "⚠Тренировка через 5 минут!",
                "Срочно готовьтесь к тренировке '$workoutName'"
            )
        }

        // 5. Уведомление в момент тренировки
        if (timestampMs > now) {
            NotificationScheduler.scheduleNotification(
                context,
                workoutName,
                timestampMs,
                "Время тренировки!",
                "Пора начинать тренировку '$workoutName'"
            )
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

    private fun loadRecommended() {
        val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)

        recommendedSection = requireView().findViewById(R.id.recommendedSection)
        recommendedContainer = requireView().findViewById(R.id.recommendedContainer)
        btnToggleRecommended = requireView().findViewById(R.id.btnToggleRecommended)

        applyRecommendedVisibility()

        btnToggleRecommended.setOnClickListener {
            showRecommended = !showRecommended
            prefs.edit().putBoolean("show_recommended", showRecommended).apply()
            applyRecommendedVisibility()
        }

        launch {
            val list = withContext(Dispatchers.IO) {
                ApiManager.getRecommendedWorkouts(userId)
            }

            if (!isAdded) return@launch

            recommendedContainer.removeAllViews()

            if (list.isEmpty()) {
                recommendedSection.visibility = View.GONE
                return@launch
            }

            list.forEach { workout ->
                val card = layoutInflater.inflate(R.layout.workout_card, recommendedContainer, false)
                card.findViewById<LinearLayout>(R.id.cardRoot).isClickable = false
                card.findViewById<LinearLayout>(R.id.cardRoot).isFocusable = false
                card.findViewById<TextView>(R.id.tvWorkoutName).text = workout.name
                card.findViewById<TextView>(R.id.tvExerciseCount).text =
                    "${workout.exercises.size} упражнений"
                card.findViewById<TextView>(R.id.tvDate).text = "⭐ Рекомендовано"

                card.findViewById<Button>(R.id.btnStartWorkout).apply {
                    text = "Добавить себе"
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        Log.d("COPY_WORKOUT", "Button clicked! workoutId=${workout.id} userId=$userId")

                        CoroutineScope(Dispatchers.Main).launch {
                            Log.d("COPY_WORKOUT", "Coroutine started")
                            try {
                                val success = withContext(Dispatchers.IO) {
                                    ApiManager.copyWorkout(userId, workout.id)
                                }
                                Log.d("COPY_WORKOUT", "Result: $success")

                                if (!isAdded) return@launch

                                if (success) {
                                    Toast.makeText(requireContext(),
                                        "✅ '${workout.name}' добавлена",
                                        Toast.LENGTH_SHORT).show()
                                    recommendedContainer.removeView(card)
                                    if (recommendedContainer.childCount == 0) {
                                        recommendedSection.visibility = View.GONE
                                    }
                                    loadWorkouts()
                                } else {
                                    Toast.makeText(requireContext(),
                                        "Тренировка уже добавлена", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Log.e("COPY_WORKOUT", "Error: ${e.message}", e)
                                if (isAdded) {
                                    Toast.makeText(requireContext(),
                                        "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                // Скрываем редактирование и удаление для рекомендованных
                card.findViewById<Button>(R.id.btnEditWorkout).visibility = View.GONE
                card.findViewById<Button>(R.id.btnDelete).visibility = View.GONE

                recommendedContainer.addView(card)
            }

            applyRecommendedVisibility()
        }
    }

    private fun applyRecommendedVisibility() {
        if (!::recommendedSection.isInitialized) return
        recommendedContainer.visibility = if (showRecommended) View.VISIBLE else View.GONE
        btnToggleRecommended.text = if (showRecommended) "Скрыть" else "Показать"
    }

}