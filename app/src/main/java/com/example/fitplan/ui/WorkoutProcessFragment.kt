package com.example.fitplan.ui

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
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Exercise
import com.example.fitplan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkoutProcessFragment : Fragment() {

    private lateinit var db: DatabaseHelper
    private var workoutId: Long = -1L
    private lateinit var containerExercises: LinearLayout
    private val activeTimers = mutableListOf<CountDownTimer>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Инициализация базы данных и получение ID тренировки из аргументов
        db = DatabaseHelper(requireContext())
        workoutId = arguments?.getLong("WORKOUT_ID", -1L) ?: -1L
        Log.d("WorkoutProcess", "Received workoutId: $workoutId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Инициализация основного layout фрагмента процесса тренировки
        val view = inflater.inflate(R.layout.fragment_workout_process, container, false)

        // Находим все View
        containerExercises = view.findViewById(R.id.containerExercises)
        val btnBack = view.findViewById<Button>(R.id.btnBack)

        // Настраиваем кнопку "Назад" - возврат на предыдущий экран
        btnBack.setOnClickListener {
            // Останавливаем все таймеры при выходе для предотвращения утечек памяти
            activeTimers.forEach { it.cancel() }
            activeTimers.clear()

            // Возвращаемся на предыдущий экран в стеке фрагментов
            parentFragmentManager.popBackStack()
        }

        // Загружаем упражнения для текущей тренировки
        loadExercises()
        return view
    }


     //Загружает упражнения текущей тренировки из базы данных

    private fun loadExercises() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Получение упражнений из базы данных
                val exercises = db.getExercises(workoutId)
                Log.d("WorkoutProcess", "Loaded ${exercises.size} exercises for workout $workoutId")

                // Обновление UI в основном потоке
                withContext(Dispatchers.Main) {
                    if (exercises.isEmpty()) {
                        // Если упражнений нет, показываем информационное сообщение
                        val noExercisesText = TextView(requireContext()).apply {
                            text = "В этой тренировке нет упражнений"
                            textSize = 16f
                            setTextColor(Color.GRAY)
                            gravity = Gravity.CENTER
                            setPadding(0, 32, 0, 32)
                        }
                        containerExercises.addView(noExercisesText)
                        return@withContext
                    }
                    // Добавляем карточку для каждого упражнения
                    exercises.forEach { exercise ->
                        addExerciseCard(exercise)
                    }
                }
            } catch (e: Exception) {
                // Обработка ошибок при загрузке упражнений
                Log.e("PROCESS_CRASH", "Ошибка загрузки упражнений: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Ошибка загрузки упражнений", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Очистка всех активных таймеров при уничтожении view для предотвращения утечек памяти
        activeTimers.forEach { it.cancel() }
        activeTimers.clear()
    }


     //Создает и добавляет карточку упражнения в интерфейс

    private fun addExerciseCard(exercise: Exercise) {
        Log.d("WorkoutProcess", "Adding exercise: ${exercise.name} (sets: ${exercise.sets}, reps: ${exercise.reps})")

        // Инфлейт макета карточки упражнения
        val card = layoutInflater.inflate(
            R.layout.item_workout_exercise,
            containerExercises,
            false
        )

        // Получение ссылок на элементы карточки
        val tvName = card.findViewById<TextView>(R.id.tvExerciseName)
        val tvSets = card.findViewById<TextView>(R.id.tvSets)
        val tvReps = card.findViewById<TextView>(R.id.tvReps)
        val tvWeight = card.findViewById<TextView>(R.id.tvWeight)
        val tvRest = card.findViewById<TextView>(R.id.tvRest)
        val containerSets = card.findViewById<LinearLayout>(R.id.containerSets)
        val btnRest = card.findViewById<Button>(R.id.btnRest)
        val btnPause = card.findViewById<Button>(R.id.btnPause)
        val tvTimer = card.findViewById<TextView>(R.id.tvTimer)

        // Установка данных упражнения в элементы интерфейса
        tvName.text = exercise.name
        tvSets.text = exercise.sets.toString()
        tvReps.text = exercise.reps.toString()
        tvWeight.text = exercise.weight.toString()
        tvRest.text = exercise.rest.toString()

        // Инициализация таймера отдыха
        tvTimer.text = formatTime(exercise.rest * 1000L)
        tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_recovery))

        // Очистка контейнера для подходов
        containerSets.removeAllViews()

        // Массив для отслеживания выполненных подходов
        val setsDone = BooleanArray(exercise.sets)
        val setViews = mutableListOf<TextView>()

        // Создание индикаторов подходов (кружки)
        repeat(exercise.sets) { index ->
            val setView = TextView(requireContext()).apply {
                text = (index + 1).toString()
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER

                // Настройка размеров и отступов
                val size = 36 // dp
                val params = LinearLayout.LayoutParams(
                    (size * resources.displayMetrics.density).toInt(),
                    (size * resources.displayMetrics.density).toInt()
                )
                if (index < exercise.sets - 1) {
                    params.marginEnd = 8
                }
                layoutParams = params

                // Установка фона (кружок)
                try {
                    val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.set_circle)
                    background = drawable ?: createCircleDrawable(Color.parseColor("#2196F3"))
                } catch (e: Exception) {
                    background = createCircleDrawable(Color.parseColor("#2196F3"))
                }
            }

            // Переменные для отслеживания кликов и состояния подхода
            var clickCount = 0
            var locked = false

            // Обработчик нажатия на индикатор подхода
            setView.setOnClickListener {
                if (locked) return@setOnClickListener

                clickCount++

                // Требуется двойное нажатие для отметки подхода как выполненного
                if (clickCount == 2) {
                    locked = true
                    setsDone[index] = true

                    // Изменение внешнего вида на выполненный
                    try {
                        val doneDrawable = ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.set_circle_done
                        )
                        setView.background = doneDrawable ?: createCircleDrawable(Color.parseColor("#4CAF50"))
                    } catch (e: Exception) {
                        setView.background = createCircleDrawable(Color.parseColor("#4CAF50"))
                    }
                    setView.setTextColor(Color.WHITE)

                    // Проверка, все ли подходы выполнены
                    val completedCount = setsDone.count { it }
                    if (completedCount == exercise.sets) {
                        // Все подходы выполнены - отключаем управление
                        disableExerciseControls(btnRest, btnPause, tvTimer, setViews)
                    }
                }
            }

            setViews.add(setView)
            containerSets.addView(setView)
        }

        // Переменные для управления таймером отдыха
        var currentTimer: CountDownTimer? = null
        var isTimerRunning = false
        var remainingTime = exercise.rest * 1000L
        var isPaused = false


         // Запускает таймер отдыха

        fun startTimer(totalTime: Long = exercise.rest * 1000L) {
            // Отмена предыдущего таймера
            currentTimer?.cancel()
            currentTimer?.let { activeTimers.remove(it) }

            // Создание нового таймера
            currentTimer = object : CountDownTimer(totalTime, 1000) {
                override fun onTick(ms: Long) {
                    remainingTime = ms
                    tvTimer.text = formatTime(ms)

                    // Изменение цвета в зависимости от оставшегося времени
                    when {
                        ms <= 5000 -> {
                            // Последние 5 секунд - красный цвет
                            tvTimer.setTextColor(Color.RED)
                            btnRest.setBackgroundColor(Color.parseColor("#F44336"))
                        }
                        ms <= 15000 -> {
                            // От 5 до 15 секунд - оранжевый цвет
                            tvTimer.setTextColor(Color.parseColor("#FF9800"))
                            btnRest.setBackgroundColor(Color.parseColor("#FF9800"))
                        }
                        else -> {
                            // Более 15 секунд - стандартный цвет
                            tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_recovery))
                            btnRest.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_progress))
                        }
                    }
                }

                override fun onFinish() {
                    // Действия по завершению таймера
                    tvTimer.text = "Готово!"
                    tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_progress))
                    btnRest.text = "Старт"
                    btnRest.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_progress))
                    btnPause.visibility = View.GONE

                    // Сброс состояния таймера
                    isTimerRunning = false
                    isPaused = false
                    remainingTime = exercise.rest * 1000L

                    // Удаление таймера из активных
                    activeTimers.remove(this)

                    // Проверяем, все ли подходы выполнены
                    val completedCount = setsDone.count { it }
                    if (completedCount == exercise.sets) {
                        disableExerciseControls(btnRest, btnPause, tvTimer, setViews)
                    }
                }
            }.start()

            // Добавление таймера в активные и обновление состояния
            currentTimer?.let { activeTimers.add(it) }
            isTimerRunning = true
            isPaused = false
            btnRest.text = "Стоп"
            btnRest.setBackgroundColor(Color.parseColor("#F44336"))
            btnPause.visibility = View.VISIBLE
        }

        // Обработчик кнопки старта/останова таймера отдыха
        btnRest.setOnClickListener {
            if (!isTimerRunning) {
                // Запуск таймера, если он не запущен
                startTimer()
            } else {
                // Остановка таймера, если он запущен
                currentTimer?.cancel()
                currentTimer?.let { activeTimers.remove(it) }
                tvTimer.text = formatTime(exercise.rest * 1000L)
                tvTimer.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_recovery))
                btnRest.text = "Старт"
                btnRest.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_progress))
                btnPause.visibility = View.GONE

                // Сброс состояния
                isTimerRunning = false
                isPaused = false
                remainingTime = exercise.rest * 1000L
            }
        }

        // Обработчик кнопки паузы/сброса таймера
        btnPause.setOnClickListener {
            if (!isPaused) {
                // Пауза таймера
                currentTimer?.cancel()
                btnPause.text = "Продолжить"
                btnPause.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_progress))
                btnPause.setTextColor(Color.WHITE)

                tvTimer.text = "${formatTime(remainingTime)}"
                tvTimer.setTextColor(Color.GRAY)

                isPaused = true
            } else {
                // Продолжение таймера после паузы
                btnPause.text = "Сброс"
                btnPause.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background_tertiary))
                btnPause.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                startTimer(remainingTime)
            }
        }

        // Добавление карточки упражнения в основной контейнер
        containerExercises.addView(card)
        Log.d("WorkoutProcess", "Exercise card added: ${exercise.name}")
    }


     //Отключает управление упражнением после выполнения всех подходов

    private fun disableExerciseControls(
        btnRest: Button,
        btnPause: Button,
        tvTimer: TextView,
        setViews: List<TextView>
    ) {
        // Останавливаем все таймеры в этом упражнении
        activeTimers.forEach { it.cancel() }
        activeTimers.clear()

        // Делаем кнопки серыми и недоступными
        btnRest.isEnabled = false
        btnRest.setBackgroundColor(Color.parseColor("#9E9E9E"))
        btnRest.text = "Готово"
        btnRest.setTextColor(Color.parseColor("#616161"))

        // Обновляем таймер
        tvTimer.text = "Готово"
        tvTimer.setTextColor(Color.parseColor("#4CAF50"))

        // Делаем индикаторы подходов недоступными для кликов
        setViews.forEach { setView ->
            setView.isEnabled = false
        }
    }


     // Форматирует время из миллисекунд в строку формата MM:SS

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }


     //Создает круглый Drawable заданного цвета

    private fun createCircleDrawable(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setSize(48, 48)
        }
    }
}