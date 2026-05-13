package com.example.fitplan.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.*
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.User
import com.example.fitplan.Models.UserDto
import com.example.fitplan.R
import com.example.fitplan.Utils.MacroCalculator
import kotlinx.coroutines.*

class EditProfileDialog(
    private var user: User,
    private val onSave: () -> Unit
) : DialogFragment() {

    private lateinit var tvCalculatedGoals: TextView
    private lateinit var spGender: Spinner
    private lateinit var spGoal: Spinner
    private lateinit var spActivity: Spinner

    private val genderMapping = mapOf(
        "Мужской" to "male",
        "Женский" to "female"
    )

    private val activityMapping = mapOf(
        "Малая активность" to "LIGHT",
        "Средняя активность" to "MODERATE",
        "Высокая активность" to "VERY_ACTIVE"
    )

    private val goalMapping = mapOf(
        "Снижение веса" to "WEIGHT_LOSS",
        "Набор массы" to "WEIGHT_GAIN",
        "Поддержание веса" to "MAINTENANCE"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_edit_profile_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etName = view.findViewById<EditText>(R.id.etName)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etAge = view.findViewById<EditText>(R.id.etAge)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val etTargetWeight = view.findViewById<EditText>(R.id.etTargetWeight)

        spGender = view.findViewById(R.id.spGender)
        spGoal = view.findViewById(R.id.spGoal)
        spActivity = view.findViewById(R.id.spActivity)

        val btnSave = view.findViewById<Button>(R.id.btnSave)
        tvCalculatedGoals = view.findViewById(R.id.tvCalculatedGoals)

        setupSpinners()

        // Заполняем поля
        etName.setText(user.name)
        etEmail.setText(user.email)
        etAge.setText(user.age?.toString() ?: "")
        etWeight.setText(user.weight?.toString() ?: "")
        etHeight.setText(user.height?.toString() ?: "")
        etTargetWeight.setText(user.targetWeight?.toString() ?: "")

        val genderIndex = genderMapping.values.indexOf(user.gender)
        if (genderIndex >= 0) spGender.setSelection(genderIndex)

        val activityIndex = activityMapping.values.indexOf(user.activity)
        if (activityIndex >= 0) spActivity.setSelection(activityIndex)

        val goalIndex = goalMapping.values.indexOf(user.goal)
        if (goalIndex >= 0) spGoal.setSelection(goalIndex)

        fun calculateAndDisplayGoals() {
            val age = etAge.text.toString().toIntOrNull()
            val weight = etWeight.text.toString().toIntOrNull()
            val height = etHeight.text.toString().toIntOrNull()
            val targetWeight = etTargetWeight.text.toString().toIntOrNull()

            val gender = getSelectedGender()
            val activity = getSelectedActivityLevel()
            val goal = getSelectedGoal()

            if (age == null || weight == null || height == null ||
                gender == null || activity == null || goal == null
            ) return

            val tempUser = user.copy(
                age = age, weight = weight, height = height,
                targetWeight = targetWeight, gender = gender,
                activity = activity, goal = goal
            )

            val macros = MacroCalculator.calculateDailyGoals(tempUser)

            tvCalculatedGoals.text = """
                КБЖУ:
                Калории: ${macros?.calories} ккал
                Белки: ${macros?.protein} г
                Жиры: ${macros?.fat} г
                Углеводы: ${macros?.carbs} г
            """.trimIndent()
        }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = calculateAndDisplayGoals()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etAge.addTextChangedListener(watcher)
        etWeight.addTextChangedListener(watcher)
        etHeight.addTextChangedListener(watcher)
        etTargetWeight.addTextChangedListener(watcher)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateAndDisplayGoals()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spGender.onItemSelectedListener = listener
        spGoal.onItemSelectedListener = listener
        spActivity.onItemSelectedListener = listener

        calculateAndDisplayGoals()

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val age = etAge.text.toString().toIntOrNull()
            val weight = etWeight.text.toString().toIntOrNull()
            val height = etHeight.text.toString().toIntOrNull()
            val targetWeight = etTargetWeight.text.toString().toIntOrNull()

            if (name.isEmpty() || email.isEmpty() || age == null || weight == null || height == null) {
                Toast.makeText(requireContext(), "Заполните поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val gender = getSelectedGender() ?: "male"
            val activity = getSelectedActivityLevel() ?: "MODERATE"
            val goal = getSelectedGoal() ?: "MAINTENANCE"

            val tempUser = user.copy(
                age = age, weight = weight, height = height,
                targetWeight = targetWeight, gender = gender,
                activity = activity, goal = goal
            )
            val macros = MacroCalculator.calculateDailyGoals(tempUser)

            btnSave.isEnabled = false
            btnSave.text = "Сохранение..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // ВАЖНО: получаем правильный server_id из SharedPreferences!
                    val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
                    val emailFromPrefs = prefs.getString("email", null)

                    // Получаем пользователя с сервера по email, чтобы узнать правильный ID
                    val users = ApiManager.getUsers()
                    val serverUser = users?.find { it.email == (emailFromPrefs ?: email) }

                    val correctServerId = serverUser!!.id

                    Log.d("EDIT_PROFILE", "=== ОТПРАВКА ===")
                    Log.d("EDIT_PROFILE", "user.id (локальный): ${user.id}")
                    Log.d("EDIT_PROFILE", "correctServerId: $correctServerId")
                    Log.d("EDIT_PROFILE", "targetWeight: $targetWeight")

                    val userDto = UserDto(
                        id = correctServerId,  // ← ИСПОЛЬЗУЕМ ПРАВИЛЬНЫЙ ID!
                        name = name,
                        email = email,
                        password = null,
                        age = age,
                        height = height,
                        weight = weight,
                        targetWeight = targetWeight,
                        activity = activity,
                        goal = goal,
                        gender = gender,
                        dailyCaloriesGoal = macros?.calories ?: 2000,
                        dailyProteinGoal = macros?.protein ?: 150,
                        dailyFatGoal = macros?.fat ?: 60,
                        dailyCarbsGoal = macros?.carbs ?: 200
                    )
                    Log.d("EDIT_PROFILE", "=== ОТПРАВКА НА СЕРВЕР ===")
                    Log.d("EDIT_PROFILE", "user.id (будет отправлен): ${user.id}")
                    Log.d("EDIT_PROFILE", "targetWeight: $targetWeight")
                    val success = ApiManager.updateUser(correctServerId, userDto)
                    if (success) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = DatabaseHelper(requireContext())

                            db.updateUserMacros(
                                correctServerId,
                                macros?.calories,
                                macros?.protein,
                                macros?.fat,
                                macros?.carbs
                            )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (success) {
                            (activity as? MainActivity3)?.refreshUserFromServer()
                            onSave()
                            Toast.makeText(requireContext(), "Профиль сохранён", Toast.LENGTH_SHORT).show()
                            dismiss()
                        } else {
                            Toast.makeText(requireContext(), "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EDIT_PROFILE", "Error", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        btnSave.isEnabled = true
                        btnSave.text = "Сохранить"
                    }
                }
            }
        }
    }

    private fun setupSpinners() {
        spGender.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, genderMapping.keys.toList())
        spActivity.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, activityMapping.keys.toList())
        spGoal.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, goalMapping.keys.toList())
    }

    private fun getSelectedGender() = genderMapping[spGender.selectedItem?.toString()]
    private fun getSelectedActivityLevel() = activityMapping[spActivity.selectedItem?.toString()]
    private fun getSelectedGoal() = goalMapping[spGoal.selectedItem?.toString()]

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}