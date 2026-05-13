package com.example.fitplan.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import com.example.fitplan.DataBase.DatabaseHelper
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.User
import com.example.fitplan.Models.Api.SyncManager
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
        Log.d("SYNC_DEBUG", "START SYNC CALL")
        bottomPanel = findViewById(R.id.bottom_navigation)
        setupPanelClicks()

        bottomPanel.visibility = View.GONE

        if (savedInstanceState == null) {

            checkAuthAndOpenFragment()

            CoroutineScope(Dispatchers.IO).launch {
                SyncManager.syncAll(this@MainActivity3)

                withContext(Dispatchers.Main) {
                    forceRefreshUser()
                }
            }
        }
    }
    // В MainActivity3.kt добавьте:
    fun getUserByLocalId(localId: Long, callback: (User?) -> Unit) {
        ioExecutor.execute {
            try {
                val db = DatabaseHelper(this)
                val user = db.getUserById(localId)  // ищем по _id
                handler.post { callback(user) }
            } catch (e: Exception) {
                Log.e("DB", "Error loading user", e)
                handler.post { callback(null) }
            }
        }
    }
    // 🔥 ГЛАВНОЕ ОБНОВЛЕНИЕ ЮЗЕРА
    fun forceRefreshUser() {
        val localUserId = sharedPref.getLong("user_id", -1)  // Это локальный _id

        loadCurrentUser(localUserId) { user ->
            currentUser = user

            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (fragment) {
                is ProfileFragment -> fragment.refreshUserData()
            }
        }
    }

    fun refreshCurrentUser() {
        val userId = sharedPref.getLong("user_id", -1)

        loadCurrentUser(userId) { user ->
            currentUser = user

            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

            if (fragment is ProfileFragment) {
                fragment.refreshUserData()
            }
        }
    }

    // ================= AUTH =================

    private fun checkAuthAndOpenFragment() {
        val prefs = getSharedPreferences("session", MODE_PRIVATE)
        val isLogged = prefs.getBoolean("logged_in", false)
        val token = prefs.getString("token", null)
        val email = prefs.getString("email", null)

        Log.d("AUTH", "isLogged=$isLogged, email=$email")

        if (!isLogged || token.isNullOrEmpty()) {
            openLogin()
            return
        }

        bottomPanel.visibility = View.VISIBLE

        // Загружаем пользователя с сервера по email
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val users = ApiManager.getUsers()
                val serverUser = users?.find { it.email == email }

                if (serverUser != null) {
                    // ВАЖНО: используем serverUser.id как локальный ID
                    val localUser = User(
                        id = serverUser.id,  // ← server_id, его и используем для обновления!
                        name = serverUser.name,
                        email = serverUser.email,
                        password = serverUser.password ?: "",
                        age = serverUser.age,
                        height = serverUser.height,
                        weight = serverUser.weight,
                        targetWeight = serverUser.targetWeight,
                        activity = serverUser.activity,
                        goal = serverUser.goal,
                        gender = serverUser.gender,
                        dailyCaloriesGoal = serverUser.dailyCaloriesGoal,
                        dailyProteinGoal = serverUser.dailyProteinGoal,
                        dailyFatGoal = serverUser.dailyFatGoal,
                        dailyCarbsGoal = serverUser.dailyCarbsGoal
                    )

                    withContext(Dispatchers.Main) {
                        currentUser = localUser
                        isUserLoaded = true
                        openTab("food")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("AUTH", "User not found on server")
                        openLogin()
                    }
                }
            } catch (e: Exception) {
                Log.e("AUTH", "Error loading user from server", e)
                withContext(Dispatchers.Main) {
                    openLogin()
                }
            }
        }
    }
    fun refreshUserFromServer() {
        val email = sharedPref.getString("email", null) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val users = ApiManager.getUsers()
                val serverUser = users?.find { it.email == email }

                if (serverUser != null) {
                    val localUser = User(
                        id = serverUser.id,
                        name = serverUser.name,
                        email = serverUser.email,
                        password = serverUser.password ?: "",
                        age = serverUser.age,
                        height = serverUser.height,
                        weight = serverUser.weight,
                        targetWeight = serverUser.targetWeight,
                        activity = serverUser.activity,
                        goal = serverUser.goal,
                        gender = serverUser.gender,
                        dailyCaloriesGoal = serverUser.dailyCaloriesGoal,
                        dailyProteinGoal = serverUser.dailyProteinGoal,
                        dailyFatGoal = serverUser.dailyFatGoal,
                        dailyCarbsGoal = serverUser.dailyCarbsGoal
                    )

                    withContext(Dispatchers.Main) {
                        currentUser = localUser
                        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                        if (fragment is ProfileFragment) {
                            fragment.refreshUserData()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("REFRESH", "Error refreshing user", e)
            }
        }
    }
    // ================= LOGIN =================

    fun onLoginSuccess(email: String) {
        bottomPanel.visibility = View.VISIBLE
        retryLoadUser(email, 10)
    }

    private fun retryLoadUser(email: String, attempts: Int) {
        loadUserByEmail(email) { user ->
            if (user != null) {
                currentUser = user
                isUserLoaded = true
                openTab("food")
            } else if (attempts > 0) {
                handler.postDelayed({
                    retryLoadUser(email, attempts - 1)
                }, 500)
            } else {
                Toast.makeText(this, "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show()
                openLogin()
            }
        }
    }

    private fun loadUserByEmail(email: String, onLoaded: (User?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val users = ApiManager.getUsers()
                val serverUser = users?.find { it.email == email }

                val user = if (serverUser != null) {
                    User(
                        id = serverUser.id,
                        name = serverUser.name,
                        email = serverUser.email,
                        password = serverUser.password ?: "",
                        age = serverUser.age,
                        height = serverUser.height,
                        weight = serverUser.weight,
                        targetWeight = serverUser.targetWeight,
                        activity = serverUser.activity,
                        goal = serverUser.goal,
                        gender = serverUser.gender,
                        dailyCaloriesGoal = serverUser.dailyCaloriesGoal,
                        dailyProteinGoal = serverUser.dailyProteinGoal,
                        dailyFatGoal = serverUser.dailyFatGoal,
                        dailyCarbsGoal = serverUser.dailyCarbsGoal
                    )
                } else null

                withContext(Dispatchers.Main) {
                    onLoaded(user)
                }
            } catch (e: Exception) {
                Log.e("LOAD", "Error loading user by email", e)
                withContext(Dispatchers.Main) {
                    onLoaded(null)
                }
            }
        }
    }
    fun updateCurrentUserFromDb() {
        val email = sharedPref.getString("email", null) ?: return
        ioExecutor.execute {
            val db = DatabaseHelper(this)
            val user = db.getUserByEmail(email)
            handler.post {
                if (user != null) {
                    currentUser = user
                    val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                    if (fragment is ProfileFragment) {
                        fragment.refreshUserData()
                    }
                }
            }
        }
    }
    // ================= LOAD USER =================

    private fun loadCurrentUser(userId: Long, onLoaded: (User?) -> Unit) {
        ioExecutor.execute {
            try {
                val db = DatabaseHelper(this)
                val user = db.getUserById(userId)

                // ОТЛАДКА
                Log.d("LOAD_USER", "Looking for user with localId=$userId")
                if (user == null) {
                    Log.e("LOAD_USER", "User NOT FOUND with id=$userId")
                    // Показываем всех пользователей в БД
                    val allUsers = db.getAllUsers()
                    Log.d("LOAD_USER", "All users in DB (${allUsers.size}):")
                    allUsers.forEach {
                        Log.d("LOAD_USER", "  - localId=${it.id}, email=${it.email}, name=${it.name}")
                    }
                } else {
                    Log.d("LOAD_USER", "Found user: ${user.email}")
                }

                db.close()
                handler.post { onLoaded(user) }
            } catch (e: Exception) {
                Log.e("DB", "Error loading user", e)
                handler.post { onLoaded(null) }
            }
        }
    }

    // ================= LOGIN SCREEN =================

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

    // ================= TABS =================

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

    // ================= NAV =================

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