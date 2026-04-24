package com.example.fitplan.ui.login

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.R
import com.example.fitplan.ui.MainActivity3
import com.example.fitplan.ui.Reg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Login : Fragment() {

    private fun logFlow(message: String) {
        Log.d("LOGIN_FLOW", message)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_login, container, false)

        val emailInput = view.findViewById<EditText>(R.id.loginEditText)
        val passwordInput = view.findViewById<EditText>(R.id.passwordEditText)
        val loginBtn = view.findViewById<Button>(R.id.loginButton)
        val registerBtn = view.findViewById<Button>(R.id.registerButton)
        val eyeBtn = view.findViewById<TextView>(R.id.passwordToggle)

        eyeBtn.setOnClickListener {
            if (passwordInput.inputType ==
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            ) {
                passwordInput.inputType = InputType.TYPE_CLASS_TEXT
                eyeBtn.text = "🙈"
            } else {
                passwordInput.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeBtn.text = "👁️"
            }
            passwordInput.setSelection(passwordInput.text.length)
        }

        loginBtn.setOnClickListener {

            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                logFlow("⚠️ EMPTY FIELDS")
                Toast.makeText(requireContext(), "Заполните поля", Toast.LENGTH_SHORT).show()
            } else {
                checkLogin(email, password)
            }
        }

        registerBtn.setOnClickListener {
            logFlow("➡️ OPEN REGISTER SCREEN")

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, Reg())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    private fun checkLogin(email: String, password: String) {

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            try {

                logFlow("🔐 LOGIN REQUEST email=$email")

                val result = ApiManager.login(email, password)

                withContext(Dispatchers.Main) {

                    if (result == null) {
                        Toast.makeText(requireContext(), "Ошибка сервера", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    if (result.success == true) {

                        val prefs = requireContext().getSharedPreferences("session", MODE_PRIVATE)

                        Log.d("LOGIN", "SAVE SESSION userId=${result.id}")

                        prefs.edit()
                            .putString("token", result.token ?: "")
                            .putLong("user_id", result.id)
                            .putBoolean("logged_in", true)
                            .apply()

                        ApiManager.clearCache()

                        Log.d("LOGIN", "SESSION SAVED = ${prefs.all}")

                        (activity as MainActivity3).onLoginSuccess(result.id)

                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Неверный логин или пароль",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {

                Log.e("LOGIN_FLOW", "ERROR", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}