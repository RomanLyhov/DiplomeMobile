package com.example.fitplan.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Фрагмент для регистрации нового пользователя
class Reg : Fragment() {

    // Создание интерфейса фрагмента регистрации
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reg, container, false)

        // Инициализация элементов интерфейса для ввода данных
        val email = view.findViewById<EditText>(R.id.emailEditText)
        val password = view.findViewById<EditText>(R.id.passwordEditText)
        val password2 = view.findViewById<EditText>(R.id.confirmPasswordEditText)
        val name = view.findViewById<EditText>(R.id.nameEditText)
        val age = view.findViewById<EditText>(R.id.ageEditText)
        val height = view.findViewById<EditText>(R.id.heightEditText)
        val weight = view.findViewById<EditText>(R.id.currentWeightEditText)
        val targetWeight = view.findViewById<EditText>(R.id.targetWeightEditText)
        val activitySpinner = view.findViewById<Spinner>(R.id.activityLevelSpinner)
        val goalSpinner = view.findViewById<Spinner>(R.id.goalSpinner)
        val maleBtn = view.findViewById<RadioButton>(R.id.maleRadioButton)
        val femaleBtn = view.findViewById<RadioButton>(R.id.femaleRadioButton)
        val registerBtn = view.findViewById<Button>(R.id.registerButton)
        val loginLink = view.findViewById<TextView>(R.id.loginTextView)

        // Кнопка для показа/скрытия пароля
        val eyeBtn = view.findViewById<TextView>(R.id.passwordToggle)
        eyeBtn.setOnClickListener {
            if (password.inputType == 129) {
                password.inputType = 1
                eyeBtn.text = "🙈"
            } else {
                password.inputType = 129
                eyeBtn.text = "👁️"
            }
        }

        setupSpinners(activitySpinner, goalSpinner)

        // Обработчик кнопки регистрации
        registerBtn.setOnClickListener {
            if (checkFields(email, password, password2, name)) {
                // Получаем выбранные значения из спиннеров и переводим в английские
                val activityValue = when (activitySpinner.selectedItem.toString()) {
                    "Низкий" -> "LIGHT"
                    "Средний" -> "MODERATE"
                    "Высокий" -> "VERY_ACTIVE"
                    else -> "MODERATE"
                }

                val goalValue = when (goalSpinner.selectedItem.toString()) {
                    "Похудеть" -> "WEIGHT_LOSS"
                    "Набрать массу" -> "WEIGHT_GAIN"
                    "Поддерживать вес" -> "MAINTENANCE"
                    else -> "MAINTENANCE"
                }

                val genderValue = if (maleBtn.isChecked) "male" else "female"

                saveUser(
                    email = email.text.toString(),
                    password = password.text.toString(),
                    name = name.text.toString(),
                    age = age.text.toString(),
                    height = height.text.toString(),
                    weight = weight.text.toString(),
                    targetWeight = targetWeight.text.toString(),
                    gender = genderValue,
                    activity = activityValue,
                    goal = goalValue
                )
            }
        }

        // Обработчик ссылки для перехода к логину
        loginLink.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    // Настройка выпадающих списков (спиннеров)
    private fun setupSpinners(activitySpinner: Spinner, goalSpinner: Spinner) {
        val activityAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            arrayOf("Низкий", "Средний", "Высокий")
        )
        activityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        activitySpinner.adapter = activityAdapter

        val goalAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            arrayOf("Похудеть", "Набрать массу", "Поддерживать вес")
        )
        goalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        goalSpinner.adapter = goalAdapter
    }

    // Проверка заполненности обязательных полей
    private fun checkFields(email: EditText, password: EditText, password2: EditText, name: EditText): Boolean {
        when {
            email.text.isEmpty() -> { toast("Введите email"); return false }
            password.text.isEmpty() -> { toast("Введите пароль"); return false }
            password.text.toString() != password2.text.toString() -> { toast("Пароли не совпадают"); return false }
            name.text.isEmpty() -> { toast("Введите имя"); return false }
        }
        return true
    }

    // Сохранение данных пользователя в базу данных
    private fun saveUser(
        email: String,
        password: String,
        name: String,
        age: String,
        height: String,
        weight: String,
        targetWeight: String,
        gender: String,
        activity: String,
        goal: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {

            val ageInt = age.toIntOrNull()
            val heightInt = height.toIntOrNull()
            val weightInt = weight.toIntOrNull()
            val targetWeightInt = targetWeight.toIntOrNull()

            Log.d("Reg", "=== ОТПРАВКА РЕГИСТРАЦИИ ===")
            Log.d("Reg", "name=$name")
            Log.d("Reg", "email=$email")
            Log.d("Reg", "age=$ageInt")
            Log.d("Reg", "height=$heightInt")
            Log.d("Reg", "weight=$weightInt")
            Log.d("Reg", "targetWeight=$targetWeightInt")
            Log.d("Reg", "gender=$gender")
            Log.d("Reg", "activity=$activity")
            Log.d("Reg", "goal=$goal")

            val tempUser = com.example.fitplan.Models.User(
                id = 0,
                name = name,
                email = email,
                password = password,
                age = ageInt,
                height = heightInt,
                weight = weightInt,
                targetWeight = targetWeightInt,
                gender = gender,
                activity = activity,
                goal = goal,
                dailyCaloriesGoal = null,
                dailyProteinGoal = null,
                dailyFatGoal = null,
                dailyCarbsGoal = null
            )

            val macros = com.example.fitplan.Utils.MacroCalculator
                .calculateDailyGoals(tempUser)

            Log.d("MACROS", "calories=${macros?.calories}")
            Log.d("MACROS", "protein=${macros?.protein}")
            Log.d("MACROS", "fat=${macros?.fat}")
            Log.d("MACROS", "carbs=${macros?.carbs}")

            val userDto = com.example.fitplan.Models.UserDto(
                id = 0,
                name = name,
                email = email,
                password = password,
                age = ageInt,
                height = heightInt,
                weight = weightInt,
                targetWeight = targetWeightInt,
                gender = gender,
                activity = activity,
                goal = goal,

                dailyCaloriesGoal = macros?.calories,
                dailyProteinGoal = macros?.protein,
                dailyFatGoal = macros?.fat,
                dailyCarbsGoal = macros?.carbs
            )

            val success = ApiManager.addUser(userDto)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(requireContext(), "Регистрация успешна", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    Toast.makeText(requireContext(), "Ошибка регистрации", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Вспомогательная функция для показа всплывающих сообщений
    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }
}