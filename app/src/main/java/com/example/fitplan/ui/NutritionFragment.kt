package com.example.fitplan.ui

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitplan.App
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.MealProductDisplay
import com.example.fitplan.Models.User
import com.example.fitplan.R
import com.example.fitplan.ui.adapters.MealsAdapter
import com.example.fitplan.ui.models.DisplayMeal
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class NutritionFragment : Fragment() {

    // Элементы интерфейса для отображения статистики питания
    private lateinit var calorieProgressBar: ProgressBar
    private lateinit var caloriesConsumedTextView: TextView
    private lateinit var caloriesRemainingTextView: TextView
    private lateinit var totalProteinTextView: TextView
    private lateinit var totalFatTextView: TextView
    private lateinit var totalCarbsTextView: TextView
    private lateinit var dailyGoalTextView: TextView
    private lateinit var mealsRecyclerView: RecyclerView
    private lateinit var dateTextView: TextView

    // ДОБАВЛЕНО: переменные для календаря
    private lateinit var weekCalendarLayout: LinearLayout
    private var currentWeekDays = listOf<Date>()

    // Список типов приемов пищи
    private val mealTypes = listOf("Завтрак", "Обед", "Ужин", "Перекус")
    private lateinit var mealsAdapter: MealsAdapter

    // Переменные для подсчета дневных итогов
    private var loadMealsJob: Job? = null
    private var loadMealProductsJob: Job? = null
    private var dailyTotalCalories = 0
    private var dailyTotalProtein = 0
    private var dailyTotalFat = 0
    private var dailyTotalCarbs = 0
    private var dailyGoal = 2000
    private var dailyProteinGoal = 120
    private var dailyFatGoal = 55
    private var dailyCarbsGoal = 250

    // Задача для периодической очистки данных
    private var cleanupJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_nutrition, container, false)

        initViews(view)
        setupRecyclerView()
        setupFragmentResultListener()
        // ДОБАВЛЕНО: инициализация календаря
        setupWeekCalendar()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadMealsForDateAsync(Date())
        startDailyCleanupCheck()
        checkAndCleanupOldData()
    }

    private fun initViews(view: View) {
        mealsRecyclerView = view.findViewById(R.id.mealsRecyclerView)
        calorieProgressBar = view.findViewById(R.id.calorieProgressBar)
        caloriesConsumedTextView = view.findViewById(R.id.caloriesConsumedTextView)
        caloriesRemainingTextView = view.findViewById(R.id.caloriesRemainingTextView)
        totalProteinTextView = view.findViewById(R.id.totalProteinTextView)
        totalFatTextView = view.findViewById(R.id.totalFatTextView)
        totalCarbsTextView = view.findViewById(R.id.totalCarbsTextView)
        dailyGoalTextView = view.findViewById(R.id.dailyGoalTextView)
        dateTextView = view.findViewById(R.id.dateTextView)
        weekCalendarLayout = view.findViewById(R.id.weekCalendarLayout)

        // Клик по дате открывает календарь
        dateTextView.setOnClickListener {
            showDatePickerDialog()
        }

        dailyGoalTextView.text = "$dailyGoal ккал"
        val currentDate = getCurrentDateFormatted()
        dateTextView.text = currentDate
    }

    private fun setupWeekCalendar() {
        currentWeekDays = getCurrentWeekDays()
        updateWeekCalendar()
    }
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
                dateTextView.text = dateFormat.format(selectedCal.time)
                selectedDate = selectedCal.time
                loadMealsForDateAsync(selectedCal.time)
                updateWeekCalendarSelection(selectedCal.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateWeekCalendarSelection(date: Date) {
        val selectedCal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (i in 0 until weekCalendarLayout.childCount) {
            val dayView = weekCalendarLayout.getChildAt(i)
            val dayNumberTextView = dayView.findViewById<TextView>(R.id.dayNumberTextView)

            // Получаем дату для этого дня
            val dayDate = currentWeekDays.getOrNull(i) ?: continue
            val dayCal = Calendar.getInstance().apply {
                time = dayDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (dayCal.timeInMillis == selectedCal.timeInMillis) {
                dayNumberTextView.setBackgroundResource(R.drawable.bg_day_circle_selected)
                dayNumberTextView.setTextColor(resources.getColor(android.R.color.white, null))
            } else {
                dayNumberTextView.setBackgroundResource(R.drawable.bg_day_circle)
                dayNumberTextView.setTextColor(resources.getColor(R.color.text_primary, null))
            }
        }
    }

    private fun getCurrentWeekDays(): List<Date> {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val weekDays = mutableListOf<Date>()
        for (i in 0..6) {
            weekDays.add(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return weekDays
    }

    private fun updateWeekCalendar() {
        weekCalendarLayout.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val dayFormat = SimpleDateFormat("E", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d", Locale.getDefault())

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        currentWeekDays.forEachIndexed { index, date ->
            val dayView = inflater.inflate(R.layout.item_week_day, weekCalendarLayout, false)

            val dayNameTextView = dayView.findViewById<TextView>(R.id.dayNameTextView)
            val dayNumberTextView = dayView.findViewById<TextView>(R.id.dayNumberTextView)

            // Устанавливаем день недели
            dayNameTextView.text = dayFormat.format(date).uppercase().take(2)

            // Устанавливаем число
            dayNumberTextView.text = dateFormat.format(date)
            val dateCalendar = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val selectedDateCalendar = Calendar.getInstance().apply {
                time = selectedDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (dateCalendar.timeInMillis == selectedDateCalendar.timeInMillis) {
                dayNumberTextView.setBackgroundResource(R.drawable.bg_day_circle_selected)
                dayNumberTextView.setTextColor(resources.getColor(android.R.color.white, null))
            } else {
                dayNumberTextView.setBackgroundResource(R.drawable.bg_day_circle)
                dayNumberTextView.setTextColor(resources.getColor(R.color.text_primary, null))
            }
           dayView.setOnClickListener {
              onDaySelected(date, index)
            }
            weekCalendarLayout.addView(dayView)
        }
    }

    private var selectedDate:Date = Date()
    private fun onDaySelected(selectedDate: Date, position:Int )
    {
        this.selectedDate=selectedDate
      val dateFormat = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
        dateTextView.text = dateFormat.format(selectedDate)
        updateWeekDaySelection(position)
        loadMealsForDate(selectedDate)
    }
    override fun onResume() {
        super.onResume()
        // Обновляем цели пользователя при возвращении на фрагмент
        val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
        val email = prefs.getString("email", null)

        if (email != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val users = ApiManager.getUsers()
                    val serverUser = users?.find { it.email == email }
                    if (serverUser != null) {
                        dailyGoal = serverUser.dailyCaloriesGoal ?: 2000
                        dailyProteinGoal = serverUser.dailyProteinGoal ?: 120
                        dailyFatGoal = serverUser.dailyFatGoal ?: 55
                        dailyCarbsGoal = serverUser.dailyCarbsGoal ?: 250

                        withContext(Dispatchers.Main) {
                            dailyGoalTextView.text = "$dailyGoal ккал"
                            updateDailySummary()
                        }
                        Log.d("NutritionFragment", "Цели обновлены с сервера: $dailyGoal ккал")
                    }
                } catch (e: Exception) {
                    Log.e("NutritionFragment", "Ошибка обновления целей", e)
                }
            }
        }

        // Перезагружаем данные для текущей даты
        loadMealsForDateAsync(selectedDate)
    }
    private fun updateWeekDaySelection(selectedPosition: Int)
    {
        for(i in 0 until weekCalendarLayout.childCount)
        {
           val dayView = weekCalendarLayout.getChildAt(i)
            val dayNumberTextView = dayView.findViewById<TextView>(R.id.dayNumberTextView)
            if(i == selectedPosition)
            {
                dayNumberTextView.setBackgroundResource(R.drawable.bg_day_circle_selected)
                dayNumberTextView.setTextColor(resources.getColor(android.R.color.white, null))
            }else
            {
                dayNumberTextView.setBackgroundResource(R.drawable.bg_day_circle)
                dayNumberTextView.setTextColor(resources.getColor(R.color.text_primary, null))
            }
        }
    }

    private fun loadMealsForDate(date: Date)
    {
        selectedDate = date

        val displayFormat = SimpleDateFormat("d.M.yyyy", Locale.getDefault())
        dateTextView.text = displayFormat.format(date)

        loadMealsForDateAsync(date)
    }

    private fun loadMealsForDateAsync(date: Date) {

        loadMealsJob?.cancel()

        loadMealsJob = viewLifecycleOwner.lifecycleScope.launch {
            try {

                val prefs = requireContext()
                    .getSharedPreferences("session", Context.MODE_PRIVATE)

                val userId = prefs.getLong("server_user_id", -1L)
                if (userId == -1L) return@launch

                // 1. ЗАБИРАЕМ ВСЕ СЕРВЕРНЫЕ ДАННЫЕ
                val start = normalizeDate(date)
                val end = start + 86_400_000

                val meals = withContext(Dispatchers.IO) {
                    ApiManager.getMeals(userId, start, end)
                }
                Log.d("MEALS_DEBUG", "USER_ID = $userId")
                Log.d("MEALS_DEBUG", "MEALS SIZE = ${meals.size}")

                meals.forEach {
                    Log.d("MEALS_DEBUG", "meal => type=${it.mealType}, date=${it.date}")
                }
                val filtered = meals
                // 4. ГРУППИРОВКА ПО ТИПУ ПРИЕМА ПИЩИ
                val grouped = filtered.groupBy {
                    it.mealType?.trim() ?: "Другое"
                }

                // 5. СБРОС СУММ
                dailyTotalCalories = 0
                dailyTotalProtein = 0
                dailyTotalFat = 0
                dailyTotalCarbs = 0

                // 6. ФОРМИРУЕМ UI
                val displayMeals = mealTypes.map { type ->

                    val items = grouped[type] ?: emptyList()

                    val calories = items.sumOf { it.calories.toInt() }
                    val protein = items.sumOf { it.protein.toInt() }
                    val fat = items.sumOf { it.fat.toInt() }
                    val carbs = items.sumOf { it.carbs.toInt() }

                    dailyTotalCalories += calories
                    dailyTotalProtein += protein
                    dailyTotalFat += fat
                    dailyTotalCarbs += carbs

                    DisplayMeal(
                        mealType = type,
                        productCount = items.size,
                        totalCalories = calories,
                        description = if (items.isEmpty())
                            "Нет добавленных продуктов"
                        else
                            "${items.size} ${getProductCountText(items.size)}",
                        isExpanded = mealsAdapter.isMealExpanded(type)
                    )
                }

                // 7. ОБНОВЛЕНИЕ UI
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        mealsAdapter.updateData(displayMeals)
                        updateDailySummary()
                    }
                }

            } catch (e: Exception) {
                Log.e("NutritionFragment", "loadMealsForDateAsync error", e)

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        mealsAdapter.updateData(
                            mealTypes.map {
                                DisplayMeal(it, 0, 0, "Ошибка загрузки", false)
                            }
                        )
                        resetDailySummary()
                    }
                }
            }
        }
    }

    private fun getProductCountText(count: Int): String {
       return when{
           count % 10 == 1 && count % 100 != 11 ->"продукт"
           count % 10 in 2..4 && (count % 100 !in 12..14)->"продукта"
           else -> "продуктов"
       }
    }
    private fun normalizeDate(date: Date): Long {
        val cal = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
    private fun setupRecyclerView() {
        mealsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        mealsRecyclerView.setHasFixedSize(true)
        mealsRecyclerView.isNestedScrollingEnabled = false

        mealsAdapter = MealsAdapter(
            meals = mutableListOf(),
            onAddClick = { mealTypeName ->
                parentFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.fragment_container, AddMealFragment.newInstance(mealTypeName))
                    addToBackStack("add_meal")
                }
            },
            onMealClick = { mealTypeName ->
                loadMealProductsAsync(mealTypeName)
            },
            onEditProduct = { product, mealType ->
                editMealProduct(product, mealType)
            },
            onDeleteProduct = { product, mealType ->
                deleteMealProduct(product, mealType)
            }
        )

        mealsRecyclerView.adapter = mealsAdapter
    }

    private fun setupFragmentResultListener() {
        parentFragmentManager.setFragmentResultListener("meal_added", viewLifecycleOwner) { _, _ ->
            loadMealsForDateAsync(selectedDate)
        }
        parentFragmentManager.setFragmentResultListener("meal_updated", viewLifecycleOwner) { _, _ ->
            loadMealsForDateAsync(selectedDate)
        }
    }

    private fun getCurrentDateFormatted(): String {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH) + 1
        val year = calendar.get(Calendar.YEAR)
        return "$day.$month.$year"
    }


    private fun checkAndCleanupOldData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
                val userId = prefs.getLong("server_user_id", -1L)
                if (userId == -1L) return@launch

                val db = App.instance.db

                val currentDate = getCurrentDateFormatted()

                val lastCheckPrefs = requireContext().getSharedPreferences("nutrition_cleanup", Context.MODE_PRIVATE)
                val lastCheckDate = lastCheckPrefs.getString("last_check_date", "")

                if (lastCheckDate != currentDate) {
                    Log.d("NutritionFragment", "Обнаружен новый день: $currentDate (было: $lastCheckDate)")

                    val archiveSuccess = withContext(Dispatchers.IO) {
                        try {
                            db.archiveOldMeals(userId)
                        } catch (e: Exception) {
                            Log.e("NutritionFragment", "Ошибка архивации: ${e.message}")
                            false
                        }
                    }

                    if (archiveSuccess) {
                        Log.d("NutritionFragment", "Старые данные успешно архивированы")

                        withContext(Dispatchers.Main) {
                            loadMealsAsync()
                        }
                    } else {
                        Log.e("NutritionFragment", "Ошибка архивации старых данных")
                    }

                    lastCheckPrefs.edit().putString("last_check_date", currentDate).apply()
                }
            } catch (e: Exception) {
                Log.e("NutritionFragment", "Ошибка проверки данных: ${e.message}")
            }
        }
    }

    private fun startDailyCleanupCheck() {
        cleanupJob?.cancel()

        cleanupJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                try {
                    checkForMidnightCleanup()
                    delay(60000)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun checkForMidnightCleanup() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        if (currentHour == 0 && currentMinute in 1..5) {
            val prefs = requireContext().getSharedPreferences("midnight_check", Context.MODE_PRIVATE)
            val alreadyCleanedToday = prefs.getBoolean("cleaned_${getCurrentDateFormatted()}", false)

            if (!alreadyCleanedToday) {
                Log.d("NutritionFragment", "Обнаружена полночь, запуск очистки")
                viewLifecycleOwner.lifecycleScope.launch {
                    performMidnightCleanup()
                }
            }
        }
    }

    private fun performMidnightCleanup() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
                val userId = prefs.getLong("server_user_id", -1L)
                if (userId == -1L) return@launch

                val db = App.instance.db

                val archiveSuccess = withContext(Dispatchers.IO) {
                    try {
                        db.archiveOldMeals(userId)
                    } catch (e: Exception) {
                        Log.e("NutritionFragment", "Ошибка архивации: ${e.message}")
                        false
                    }
                }

                if (archiveSuccess) {
                    Log.d("NutritionFragment", "Полуночная архивация выполнена успешно")

                    withContext(Dispatchers.Main) {
                        resetDailySummary()
                        mealsAdapter.updateData(emptyList())

                        Toast.makeText(
                            requireContext(),
                            "Данные за предыдущий день сохранены в архив",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    val midnightPrefs = requireContext().getSharedPreferences("midnight_check", Context.MODE_PRIVATE)
                    midnightPrefs.edit().putBoolean("cleaned_${getCurrentDateFormatted()}", true).apply()
                }
            } catch (e: Exception) {
                Log.e("NutritionFragment", "Ошибка полуночной очистки: ${e.message}")
            }
        }
    }

    private fun loadMealsAsync() {
        loadMealsJob?.cancel()

        loadMealsJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
                val userId = prefs.getLong("server_user_id", -1L)
                if (userId == -1L) return@launch

                val db = App.instance.db
                val user = withContext(Dispatchers.IO) {
                    db.getUserById(userId)
                }
                user?.let {
                    updateDailyGoalsFromProfile(it)
                }

                val mealSummaries = withContext(Dispatchers.IO) {
                    mealTypes.mapNotNull { mealType ->
                        db.getMealSummaryByType(userId, mealType)
                    }
                }

                val displayMeals = mutableListOf<DisplayMeal>()

                dailyTotalCalories = 0
                dailyTotalProtein = 0
                dailyTotalFat = 0
                dailyTotalCarbs = 0

                mealTypes.forEach { mealType ->
                    val summary = mealSummaries.firstOrNull {
                        it.mealType.equals(mealType, ignoreCase = true)
                    }

                    if (summary != null) {
                        dailyTotalCalories += summary.totalCalories
                        dailyTotalProtein += summary.totalProtein
                        dailyTotalFat += summary.totalFat
                        dailyTotalCarbs += summary.totalCarbs

                        displayMeals.add(DisplayMeal(
                            mealType = mealType,
                            productCount = summary.productCount,
                            totalCalories = summary.totalCalories,
                            description = "${summary.productCount} продукт(ов)",
                            isExpanded = mealsAdapter.isMealExpanded(mealType)
                        ))
                    } else {
                        displayMeals.add(DisplayMeal(
                            mealType = mealType,
                            productCount = 0,
                            totalCalories = 0,
                            description = "Нет добавленных продуктов",
                            isExpanded = mealsAdapter.isMealExpanded(mealType)
                        ))
                    }
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        mealsAdapter.updateData(displayMeals)
                        updateDailySummary()
                        Log.d("NutritionFragment", "Загружено ${displayMeals.size} приемов пищи")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        val emptyMeals = mealTypes.map { mealType ->
                            DisplayMeal(mealType, 0, 0, "Нет данных", false)
                        }
                        mealsAdapter.updateData(emptyMeals)
                        resetDailySummary()
                    }
                }
            }
        }
    }

    private suspend fun updateDailyGoalsFromProfile(user: User) {
        val newDailyGoal = user.dailyCaloriesGoal ?: 2000
        val newProteinGoal = user.dailyProteinGoal ?: calculateDefaultProtein(user.weight ?: 70)
        val newFatGoal = user.dailyFatGoal ?: calculateDefaultFat(newDailyGoal)
        val newCarbsGoal = user.dailyCarbsGoal ?: calculateDefaultCarbs(
            newDailyGoal,
            newProteinGoal,
            newFatGoal
        )
        dailyGoal = newDailyGoal
        dailyProteinGoal = newProteinGoal
        dailyFatGoal = newFatGoal
        dailyCarbsGoal = newCarbsGoal

        withContext(Dispatchers.Main) {
            if (isAdded) {
                dailyGoalTextView.text = "$dailyGoal ккал"
            }
        }
    }

    private fun calculateDefaultProtein(weight: Int): Int {
        return (weight * 1.8).toInt()
    }

    private fun calculateDefaultFat(dailyCalories: Int): Int {
        return ((dailyCalories * 0.25) / 9).toInt()
    }

    private fun calculateDefaultCarbs(dailyCalories: Int, proteinGrams: Int, fatGrams: Int): Int {
        val proteinCalories = proteinGrams * 4
        val fatCalories = fatGrams * 9
        val remainingCalories = dailyCalories - proteinCalories - fatCalories
        return (remainingCalories / 4).toInt()
    }

    private fun loadMealProductsAsync(mealType: String) {

        loadMealProductsJob?.cancel()

        loadMealProductsJob = viewLifecycleOwner.lifecycleScope.launch {

            try {

                val prefs = requireContext()
                    .getSharedPreferences("session", Context.MODE_PRIVATE)

                val userId = prefs.getLong("server_user_id", -1L)

                if (userId == -1L) return@launch

                val start = normalizeDate(selectedDate)
                val end = start + 86_400_000

                val meals = withContext(Dispatchers.IO) {
                    ApiManager.getMeals(userId, start, end)
                }

                Log.d("MEAL_DEBUG", "CLICKED TYPE = $mealType")
                Log.d("MEAL_DEBUG", "SERVER SIZE = ${meals.size}")

                meals.forEach {
                    Log.d(
                        "MEAL_DEBUG",
                        "mealType=${it.mealType} product=${it.productName}"
                    )
                }

                val normalizedClickedType = when (mealType.trim().lowercase()) {
                    "завтрак" -> listOf("завтрак", "breakfast")
                    "обед" -> listOf("обед", "lunch")
                    "ужин" -> listOf("ужин", "dinner")
                    "перекус" -> listOf("перекус", "snack")
                    else -> listOf(mealType.trim().lowercase())
                }

                val filtered = meals.filter {

                    val serverType = it.mealType
                        ?.trim()
                        ?.lowercase()

                    serverType in normalizedClickedType
                }

                Log.d("MEAL_DEBUG", "FILTERED = ${filtered.size}")

                val products = filtered.mapNotNull {

                    if (it.productName.isNullOrBlank()) {
                        null
                    } else {

                        MealProductDisplay(
                            mealItemId = it.id,
                            productId = it.productId,
                            productName = it.productName,
                            quantity = it.quantity,

                            calories = it.calories.toFloat(),
                            protein = it.protein.toFloat(),
                            fat = it.fat.toFloat(),
                            carbs = it.carbs.toFloat()
                        )
                    }
                }

                withContext(Dispatchers.Main) {

                    if (!isAdded) return@withContext

                    Log.d("MEAL_DEBUG", "FOUND PRODUCTS = ${products.size}")

                    // 🔥 ГЛАВНОЕ ИСПРАВЛЕНИЕ
                    mealsAdapter.updateMealProducts(
                        mealType,
                        products
                    )

                    // обновляем раскрытие
                    mealsAdapter.notifyDataSetChanged()

                    if (products.isEmpty()) {

                        Toast.makeText(
                            requireContext(),
                            "Нет продуктов в $mealType",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    "NutritionFragment",
                    "loadMealProductsAsync error",
                    e
                )
            }
        }
    }

    private fun showMealProductsBottomSheet(
        products: List<MealProductDisplay>,
        mealType: String
    ) {
        // временно хотя бы лог/Toast
        Log.d("NutritionFragment", "SHOW UI FOR $mealType -> ${products.size}")

        Toast.makeText(
            requireContext(),
            "$mealType: ${products.size} продуктов",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateDailySummary() {
        calorieProgressBar.max = dailyGoal
        calorieProgressBar.progress = dailyTotalCalories.coerceAtMost(dailyGoal)
        caloriesConsumedTextView.text = " $dailyTotalCalories"
        caloriesRemainingTextView.text = "Осталось: ${(dailyGoal - dailyTotalCalories).coerceAtLeast(0)} ккал"
        totalProteinTextView.text = "$dailyTotalProtein / $dailyProteinGoal г"
        totalFatTextView.text = "$dailyTotalFat / $dailyFatGoal г"
        totalCarbsTextView.text = "$dailyTotalCarbs / $dailyCarbsGoal г"

        val remaining = dailyGoal - dailyTotalCalories
        if (remaining < 0) {
            caloriesRemainingTextView.setTextColor(resources.getColor(android.R.color.holo_red_dark, null))
        } else {
            caloriesRemainingTextView.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        }

        val progressPercent = (dailyTotalCalories.toFloat() / dailyGoal) * 100
        when {
            progressPercent > 100 -> calorieProgressBar.progressTintList =
                android.content.res.ColorStateList.valueOf(resources.getColor(android.R.color.holo_red_dark, null))
            progressPercent > 80 -> calorieProgressBar.progressTintList =
                android.content.res.ColorStateList.valueOf(resources.getColor(android.R.color.holo_orange_dark, null))
            else -> calorieProgressBar.progressTintList =
                android.content.res.ColorStateList.valueOf(resources.getColor(android.R.color.holo_green_dark, null))
        }
    }

    private fun resetDailySummary() {
        dailyTotalCalories = 0
        dailyTotalProtein = 0
        dailyTotalFat = 0
        dailyTotalCarbs = 0

        calorieProgressBar.progress = 0
        caloriesConsumedTextView.text = "Съедено: 0 ккал"
        caloriesRemainingTextView.text = "Осталось: $dailyGoal ккал"
        totalProteinTextView.text = "0 / $dailyProteinGoal г"
        totalFatTextView.text = "0 / $dailyFatGoal г"
        totalCarbsTextView.text = "0 / $dailyCarbsGoal г"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadMealsJob?.cancel()
        loadMealProductsJob?.cancel()
        cleanupJob?.cancel()
    }

    private fun editMealProduct(product: MealProductDisplay, mealType: String) {
        val input = EditText(requireContext()).apply {
            setText(product.quantity.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Редактировать количество")
            .setMessage("Введите новое количество (г) для ${product.productName}:")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newQuantity = input.text.toString().toIntOrNull()
                if (newQuantity != null && newQuantity > 0) {
                    updateMealProduct(product, newQuantity, mealType)
                } else {
                    Toast.makeText(requireContext(), "Введите корректное количество", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateMealProduct(product: MealProductDisplay, newQuantity: Int, mealType: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
                val userId = prefs.getLong("server_user_id", -1L)
                if (userId == -1L) return@launch

                val db = App.instance.db

                val productInfo = withContext(Dispatchers.IO) {
                    db.getProductById(product.productId)
                }

                if (productInfo != null) {
                    val factor = newQuantity / 100f

                    val success = withContext(Dispatchers.IO) {
                        db.updateMealItem(
                            product.mealItemId,
                            newQuantity,
                            productInfo.calories * factor,
                            productInfo.protein * factor,
                            productInfo.fat * factor,
                            productInfo.carbs * factor
                        )
                    }

                    withContext(Dispatchers.Main) {
                        if (isAdded) {
                            if (success) {
                                Toast.makeText(requireContext(), "Продукт обновлен", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.setFragmentResult("meal_updated", Bundle.EMPTY)
                                loadMealsForDateAsync(selectedDate)
                            } else {
                                Toast.makeText(requireContext(), "Ошибка обновления", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun deleteMealProduct(product: MealProductDisplay, mealType: String) {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Удалить продукт")
            .setMessage("Вы уверены, что хотите удалить ${product.productName}?")
            .setPositiveButton("Удалить") { _, _ ->
                performDeleteMealProduct(product, mealType)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performDeleteMealProduct(product: MealProductDisplay, mealType: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val db = App.instance.db

                val success = withContext(Dispatchers.IO) {
                    db.deleteMealItem(product.mealItemId)
                }

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (success) {
                            Toast.makeText(requireContext(), "Продукт удален", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.setFragmentResult("meal_updated", Bundle.EMPTY)
                            loadMealsForDateAsync(selectedDate)
                        } else {
                            Toast.makeText(requireContext(), "Ошибка удаления", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}