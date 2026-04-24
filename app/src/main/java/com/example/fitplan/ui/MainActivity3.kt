package com.example.fitplan.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.Api.SyncManager
import com.example.fitplan.Models.User
import com.example.fitplan.R
import com.example.fitplan.ui.login.Login
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class MainActivity3 : AppCompatActivity() {

    private lateinit var bottomPanel: LinearLayout
    private var currentTab: String = ""
    var currentUser: User? = null
    private var isUserLoaded = false

    private val handler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val sharedPref by lazy {
        getSharedPreferences("session", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main3)

        bottomPanel = findViewById(R.id.bottom_navigation)
        setupPanelClicks()

        bottomPanel.visibility = View.GONE

        if (savedInstanceState == null) {

            checkAuthAndOpenFragment() // ← СРАЗУ UI

            CoroutineScope(Dispatchers.IO).launch {
                SyncManager.syncAll(this@MainActivity3) // ← НЕ БЛОКИРУЕТ UI
            }
        }
    }

    // ===================== AUTH =====================

    private fun checkAuthAndOpenFragment() {

        val prefs = getSharedPreferences("session", MODE_PRIVATE)

        val isLogged = prefs.getBoolean("logged_in", false)
        val userId = prefs.getLong("user_id", -1L)
        val token = prefs.getString("token", null)

        Log.d("AUTH", "isLogged=$isLogged userId=$userId token=$token")

        if (!isLogged || userId == -1L || token.isNullOrEmpty()) {
            openLogin()
            return
        }

        bottomPanel.visibility = View.VISIBLE

        loadCurrentUser(userId) { user ->
            if (user != null) {
                currentUser = user
                isUserLoaded = true
                openTab("food")
            } else {
                openLogin()
            }
        }
    }

    // ===================== LOGIN SUCCESS =====================

    fun onLoginSuccess(userId: Long) {

        Log.d("LOGIN", "onLoginSuccess userId=$userId")

        bottomPanel.visibility = View.VISIBLE

        loadCurrentUser(userId) { user ->
            if (user != null) {
                currentUser = user
                isUserLoaded = true
                openTab("food")
            } else {
                openLogin()
            }
        }
    }

    // ===================== LOAD USER =====================

    private fun loadCurrentUser(userId: Long, onLoaded: (User?) -> Unit) {

        ioExecutor.execute {
            try {
                val db = DatabaseHelper(this)
                val user = db.getUserById(userId)
                db.close()

                handler.post {
                    onLoaded(user)
                }

            } catch (e: Exception) {
                handler.post {
                    onLoaded(null)
                }
            }
        }
    }

    // ===================== LOGIN SCREEN =====================

    private fun openLogin() {

        currentUser = null
        isUserLoaded = false

        handler.post {
            bottomPanel.visibility = View.GONE

            supportFragmentManager.commit {
                replace(R.id.fragment_container, Login())
            }
        }
    }

    // ===================== TABS =====================

    private fun openTab(tab: String) {

        if (currentTab == tab) return
        currentTab = tab

        val fragment = when (tab) {
            "food" -> NutritionFragment()
            "workout" -> WorkoutFragment()
            "profile" -> ProfileFragment()
            else -> return
        }

        handler.post {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, fragment)
            }
            setActive(tab)
        }
    }

    // ===================== NAV =====================

    private fun setupPanelClicks() {

        findViewById<LinearLayout>(R.id.nav_food).setOnClickListener {
            openTab("food")
        }

        findViewById<LinearLayout>(R.id.nav_workout).setOnClickListener {
            openTab("workout")
        }

        findViewById<LinearLayout>(R.id.nav_profile).setOnClickListener {
            openTab("profile")
        }
    }

    private fun setActive(tab: String) {

        setColor(R.id.nav_food, R.color.bottom_nav_inactive)
        setColor(R.id.nav_workout, R.color.bottom_nav_inactive)
        setColor(R.id.nav_profile, R.color.bottom_nav_inactive)

        when (tab) {
            "food" -> setColor(R.id.nav_food, R.color.bottom_nav_active)
            "workout" -> setColor(R.id.nav_workout, R.color.bottom_nav_active)
            "profile" -> setColor(R.id.nav_profile, R.color.bottom_nav_active)
        }
    }

    private fun setColor(id: Int, color: Int) {
        val view = findViewById<LinearLayout>(id)
        val icon = view.getChildAt(0) as ImageView
        val text = view.getChildAt(1) as TextView

        val c = ContextCompat.getColor(this, color)
        icon.setColorFilter(c)
        text.setTextColor(c)
    }
}