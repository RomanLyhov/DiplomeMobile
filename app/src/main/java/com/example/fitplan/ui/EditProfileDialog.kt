package com.example.fitplan.ui

import android.annotation.SuppressLint
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
import com.example.fitplan.Models.User
import com.example.fitplan.Models.Api.SyncManager
import com.example.fitplan.R
import com.example.fitplan.Utils.MacroCalculator
import kotlinx.coroutines.*

class EditProfileDialog(
    private var user: User,
    private val db: DatabaseHelper,
    private val onSave: () -> Unit
) : DialogFragment() {

    private lateinit var tvCalculatedGoals: TextView
    private lateinit var spGender: Spinner
    private lateinit var spGoal: Spinner
    private lateinit var spActivity: Spinner

    private var lastCalculatedGoals: MacroCalculator.MacroGoals? = null

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

        lifecycleScope.launch {

            val freshUser = withContext(Dispatchers.IO) {
                db.getUserById(user.id)
            }

            if (freshUser != null) {
                user = freshUser
            }

            fillFields(view)

        }

        @SuppressLint("SetTextI18n")
        fun calculateAndDisplayGoals() {
            val age = etAge.text.toString().toIntOrNull()
            val weight = etWeight.text.toString().toIntOrNull()
            val height = etHeight.text.toString().toIntOrNull()

            val gender = getSelectedGender()
            val activity = getSelectedActivityLevel()
            val goal = getSelectedGoal()

            if (age == null || weight == null || height == null ||
                gender == null || activity == null || goal == null
            ) return

            val tempUser = user.copy(
                age = age,
                weight = weight,
                height = height,
                gender = gender,
                activity = activity,
                goal = goal
            )

            lastCalculatedGoals = MacroCalculator.calculateDailyGoals(tempUser)

            tvCalculatedGoals.text = """
                КБЖУ:
                Калории: ${lastCalculatedGoals?.calories} ккал
                Белки: ${lastCalculatedGoals?.protein} г
                Жиры: ${lastCalculatedGoals?.fat} г
                Углеводы: ${lastCalculatedGoals?.carbs} г
            """.trimIndent()
        }



        fun attachAutoCalc() {

            val watcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = calculateAndDisplayGoals()
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }

            etAge.addTextChangedListener(watcher)
            etWeight.addTextChangedListener(watcher)
            etHeight.addTextChangedListener(watcher)
            etTargetWeight.addTextChangedListener(watcher)

            val listener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    calculateAndDisplayGoals()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            spGender.onItemSelectedListener = listener
            spGoal.onItemSelectedListener = listener
            spActivity.onItemSelectedListener = listener
        }

        attachAutoCalc()
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

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Некорректный email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedBase = user.copy(
                name = name,
                email = email,
                age = age,
                weight = weight,
                height = height,
                targetWeight = targetWeight,
                gender = getSelectedGender() ?: "male",
                activity = getSelectedActivityLevel() ?: "MODERATE",
                goal = getSelectedGoal() ?: "MAINTENANCE"
            )

            val calc = MacroCalculator.calculateDailyGoals(updatedBase)
            Log.d("DEBUG", "UPDATED BASE = $updatedBase")
            Log.d("DEBUG", "CALCULATED = $calc")
            val updatedUser = updatedBase.copy(
                dailyCaloriesGoal = calc?.calories,
                dailyProteinGoal = calc?.protein,
                dailyFatGoal = calc?.fat,
                dailyCarbsGoal = calc?.carbs
            )

            viewLifecycleOwner.lifecycleScope.launch {

                try {
                    withContext(Dispatchers.IO) {
                        db.updateUser(updatedUser)
                        SyncManager.syncAll(requireContext().applicationContext)
                    }

                    if (!isAdded) return@launch

                    onSave()
                    Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show()

                    dismissAllowingStateLoss()   // 🔥 ВОТ ЭТО ВАЖНО

                } catch (e: Exception) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun fillFields(view: View) {

        val etName = view.findViewById<EditText>(R.id.etName)
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etAge = view.findViewById<EditText>(R.id.etAge)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val etHeight = view.findViewById<EditText>(R.id.etHeight)
        val etTargetWeight = view.findViewById<EditText>(R.id.etTargetWeight)

        etName.setText(user.name)
        etEmail.setText(user.email)
        etAge.setText(user.age?.toString() ?: "")
        etWeight.setText(user.weight?.toString() ?: "")
        etHeight.setText(user.height?.toString() ?: "")
        etTargetWeight.setText(user.targetWeight?.toString() ?: "")

        setSpinnerSelection()
    }

    private fun setSpinnerSelection() {

        val genderIndex = genderMapping.values.indexOf(user.gender)
        if (genderIndex >= 0) spGender.setSelection(genderIndex)

        val activityIndex = activityMapping.values.indexOf(user.activity)
        if (activityIndex >= 0) spActivity.setSelection(activityIndex)

        val goalIndex = goalMapping.values.indexOf(user.goal)
        if (goalIndex >= 0) spGoal.setSelection(goalIndex)
    }
    private fun setupSpinners() {
        spGender.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            genderMapping.keys.toList()
        )

        spActivity.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            activityMapping.keys.toList()
        )

        spGoal.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            goalMapping.keys.toList()
        )
    }

    private fun getSelectedGender() = genderMapping[spGender.selectedItem?.toString()]
    private fun getSelectedActivityLevel() = activityMapping[spActivity.selectedItem?.toString()]
    private fun getSelectedGoal() = goalMapping[spGoal.selectedItem?.toString()]

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}