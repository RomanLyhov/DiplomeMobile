package com.example.fitplan.DataBase

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.fitplan.Models.*
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.Api.ProductDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "FitPlanDB.db"
        private const val DATABASE_VERSION = 15

        const val TABLE_USERS = "users"
        const val TABLE_WORKOUTS = "workouts"
        const val TABLE_EXERCISES = "exercises"
        const val TABLE_WORKOUT_EXERCISES = "workout_exercises"
        const val TABLE_PRODUCTS = "products"
        const val TABLE_NUTRITION_LOG = "nutrition_log"
        const val TABLE_NUTRITION_ARCHIVE = "nutrition_archive"
        const val COL_ID = "_id"
        const val COL_USER_ID = "user_id"
        const val COL_PRODUCT_ID = "product_id"
        const val COL_NAME = "name"
        const val COL_EMAIL = "email"
        const val COL_PASSWORD = "password"
    }

    override fun onCreate(db: SQLiteDatabase) {

        Log.d("DB", "Creating ALL tables from scratch...")
        createTables(db)
    }
    fun markUserUnsynced(userId: Long) {
        val db = this.writableDatabase

        val values = ContentValues().apply {
            put("synced", 0)
        }

        db.update(
            "users",
            values,
            "_id = ?",
            arrayOf(userId.toString())
        )
    }
    private fun createTables(db: SQLiteDatabase) {
        try {

            db.execSQL("""
                CREATE TABLE $TABLE_USERS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                     server_id INTEGER UNIQUE,
                    $COL_NAME TEXT,
                    $COL_EMAIL TEXT,
                    $COL_PASSWORD TEXT,
                    age INTEGER,
                    height INTEGER,
                    current_weight INTEGER,
                    target_weight INTEGER,
                    activity_level TEXT,
                    activity TEXT,
                    goal TEXT,
                    gender TEXT,
                    register_date INTEGER,
                    profile_image TEXT,
                    daily_calories_goal INTEGER,
                    daily_protein_goal INTEGER,
                    daily_fat_goal INTEGER,
                    daily_carbs_goal INTEGER,
                    synced INTEGER DEFAULT 0
                )
            """)
            Log.d("DB", "✓ Table $TABLE_USERS created")

            db.execSQL("""
                CREATE TABLE $TABLE_WORKOUTS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_USER_ID INTEGER,
                    $COL_NAME TEXT,
                    created_at INTEGER,
                    server_id INTEGER,
                    synced INTEGER DEFAULT 0
                )
            """)
            Log.d("DB", "✓ Table $TABLE_WORKOUTS created")

            db.execSQL("""
                CREATE TABLE $TABLE_EXERCISES (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_NAME TEXT
                    
                )
            """)
            Log.d("DB", "✓ Table $TABLE_EXERCISES created")

            db.execSQL("""
                CREATE TABLE $TABLE_WORKOUT_EXERCISES (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    workout_id INTEGER,
                    exercise_id INTEGER,
                    sets INTEGER,
                    reps INTEGER,
                    weight INTEGER,
                    rest INTEGER
                )
            """)
            Log.d("DB", "✓ Table $TABLE_WORKOUT_EXERCISES created")


            db.execSQL("""
                CREATE TABLE $TABLE_PRODUCTS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_NAME TEXT UNIQUE,
                    calories REAL,
                    protein REAL,
                    fat REAL,
                    carbs REAL
                )
            """)
            Log.d("DB", "✓ Table $TABLE_PRODUCTS created")

            db.execSQL("""
                CREATE TABLE $TABLE_NUTRITION_LOG (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_USER_ID INTEGER,
                    $COL_PRODUCT_ID INTEGER,
                    meal_type TEXT,
                    quantity INTEGER,
                    calories REAL,
                    protein REAL,
                    fat REAL,
                    carbs REAL,
                    date INTEGER
                )
            """)
            Log.d("DB", "✓ Table $TABLE_NUTRITION_LOG created")

            db.execSQL("""
                CREATE TABLE $TABLE_NUTRITION_ARCHIVE (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_USER_ID INTEGER,
                    $COL_PRODUCT_ID INTEGER,
                    product_name TEXT,
                    meal_type TEXT,
                    quantity INTEGER,
                    calories REAL,
                    protein REAL,
                    fat REAL,
                    carbs REAL,
                    date INTEGER,
                    archive_date INTEGER
                )
            """)
            Log.d("DB", " Table $TABLE_NUTRITION_ARCHIVE created")

            db.execSQL("""
                CREATE INDEX idx_products_name ON $TABLE_PRODUCTS($COL_NAME COLLATE NOCASE)
            """)
            Log.d("DB", " Index idx_products_name created")

            checkAllTablesCreated(db)

        } catch (e: Exception) {
            Log.e("DB", " ERROR creating tables: ${e.message}")
            Log.e("DB", "Stack trace:", e)
            throw e
        }
    }

    private fun checkAllTablesCreated(db: SQLiteDatabase) {
        val expectedTables = listOf(
            TABLE_USERS, TABLE_WORKOUTS, TABLE_EXERCISES,
            TABLE_WORKOUT_EXERCISES, TABLE_PRODUCTS,
            TABLE_NUTRITION_LOG, TABLE_NUTRITION_ARCHIVE
        )

        Log.d("DB", "=== Проверка создания таблиц ===")
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'",
            null
        )

        val createdTables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val tableName = cursor.getString(0)
            createdTables.add(tableName)
            Log.d("DB", "Найдена таблица: $tableName")
        }
        cursor.close()

        expectedTables.forEach { table ->
            if (table in createdTables) {
                Log.d("DB", " Таблица '$table' создана")
            } else {
                Log.e("DB", " Таблица '$table' НЕ создана!")
            }
        }
        Log.d("DB", "=== Проверка завершена ===")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS users")
        db.execSQL("DROP TABLE IF EXISTS workouts")
        db.execSQL("DROP TABLE IF EXISTS exercises")
        db.execSQL("DROP TABLE IF EXISTS workout_exercises")
        db.execSQL("DROP TABLE IF EXISTS products")
        db.execSQL("DROP TABLE IF EXISTS nutrition_log")
        db.execSQL("DROP TABLE IF EXISTS nutrition_archive")
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        Log.d("DB", "Database opened")
        ensureAllTablesExist(db)
    }

    private fun ensureAllTablesExist(db: SQLiteDatabase) {
        val expectedTables = listOf(
            TABLE_USERS, TABLE_WORKOUTS, TABLE_EXERCISES,
            TABLE_WORKOUT_EXERCISES, TABLE_PRODUCTS,
            TABLE_NUTRITION_LOG, TABLE_NUTRITION_ARCHIVE
        )

        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'",
            null
        )

        val existingTables = mutableListOf<String>()
        while (cursor.moveToNext()) {
            existingTables.add(cursor.getString(0))
        }
        cursor.close()

        expectedTables.forEach { table ->
            if (table !in existingTables) {
                Log.e("DB", " Table $table is missing! Recreating...")
                createMissingTable(db, table)
            }
        }
    }



    private fun createMissingTable(db: SQLiteDatabase, tableName: String) {
        when (tableName) {
            TABLE_USERS -> {
                db.execSQL("""
                    CREATE TABLE $TABLE_USERS (
                        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_NAME TEXT,
                        $COL_EMAIL TEXT,
                        $COL_PASSWORD TEXT,
                        age INTEGER,
                        height INTEGER,
                        current_weight INTEGER,
                        target_weight INTEGER,
                        activity_level TEXT,
                        goal TEXT,
                        gender TEXT,
                        register_date INTEGER,
                        profile_image TEXT,
                        daily_calories_goal INTEGER,
                        daily_protein_goal INTEGER,
                        daily_fat_goal INTEGER,
                        daily_carbs_goal INTEGER,
                        synced INTEGER DEFAULT 0
                    )
                """)
            }
            TABLE_WORKOUTS -> {
                db.execSQL("""
                    CREATE TABLE $TABLE_WORKOUTS (
                        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_USER_ID INTEGER,
                        $COL_NAME TEXT,
                        created_at INTEGER,
                        synced INTEGER DEFAULT 0,
                        server_id INTEGER
                    )
                """)
            }
            TABLE_EXERCISES -> {
                db.execSQL("""
                    CREATE TABLE $TABLE_EXERCISES (
                        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_NAME TEXT
                    )
                """)
            }
            TABLE_WORKOUT_EXERCISES -> {
                db.execSQL("""
                    CREATE TABLE $TABLE_WORKOUT_EXERCISES (
                        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        workout_id INTEGER,
                        exercise_id INTEGER,
                        sets INTEGER,
                        reps INTEGER,
                        weight INTEGER,
                        rest INTEGER,
                        synced INTEGER DEFAULT 0
                    )
                """)
            }
            TABLE_PRODUCTS -> {
                db.execSQL("""
                    CREATE TABLE $TABLE_PRODUCTS (
                        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_NAME TEXT UNIQUE,
                        calories REAL,
                        protein REAL,
                        fat REAL,
                        carbs REAL
                    )
                """)
            }
            TABLE_NUTRITION_LOG -> {
                db.execSQL("""
                    CREATE TABLE $TABLE_NUTRITION_LOG (
                        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_USER_ID INTEGER,
                        $COL_PRODUCT_ID INTEGER,
                        meal_type TEXT,
                        quantity INTEGER,
                        calories REAL,
                        protein REAL,
                        fat REAL,
                        carbs REAL,
                        date INTEGER
                    )
                """)
            }
            TABLE_NUTRITION_ARCHIVE -> {
                db.execSQL("""
                    CREATE TABLE $TABLE_NUTRITION_ARCHIVE (
                        $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        $COL_USER_ID INTEGER,
                        $COL_PRODUCT_ID INTEGER,
                        product_name TEXT,
                        meal_type TEXT,
                        quantity INTEGER,
                        calories REAL,
                        protein REAL,
                        fat REAL,
                        carbs REAL,
                        date INTEGER,
                        archive_date INTEGER
                    )
                """)
            }
        }
        Log.d("DB", " Created missing table: $tableName")
    }
    private fun getIntOrNull(cursor: Cursor, columnName: String): Int? {
        val index = cursor.getColumnIndex(columnName)
        return if (index != -1 && !cursor.isNull(index)) cursor.getInt(index) else null
    }

    private fun getLongOrNull(cursor: Cursor, columnName: String): Long? {
        val index = cursor.getColumnIndex(columnName)
        return if (index != -1 && !cursor.isNull(index)) cursor.getLong(index) else null
    }

    private fun getStringOrNull(cursor: Cursor, columnName: String): String? {
        val index = cursor.getColumnIndex(columnName)
        return if (index != -1 && !cursor.isNull(index)) cursor.getString(index) else null
    }


    private fun querySingle(
        table: String,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): ContentValues? {
        return readableDatabase.query(
            table, null, selection, selectionArgs, null, null, null, "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val contentValues = ContentValues()
                for (i in 0 until cursor.columnCount) {
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> Unit
                        Cursor.FIELD_TYPE_INTEGER -> contentValues.put(cursor.getColumnName(i), cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> contentValues.put(cursor.getColumnName(i), cursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> contentValues.put(cursor.getColumnName(i), cursor.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> contentValues.put(cursor.getColumnName(i), cursor.getBlob(i))
                    }
                }
                contentValues
            } else {
                null
            }
        }
    }

    private fun queryMultiple(
        table: String,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        orderBy: String? = null,
        limit: String? = null
    ): List<ContentValues> {
        return readableDatabase.query(
            table, null, selection, selectionArgs, null, null, orderBy, limit
        ).use { cursor ->
            val result = mutableListOf<ContentValues>()
            while (cursor.moveToNext()) {
                val contentValues = ContentValues()
                for (i in 0 until cursor.columnCount) {
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> Unit
                        Cursor.FIELD_TYPE_INTEGER -> contentValues.put(cursor.getColumnName(i), cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> contentValues.put(cursor.getColumnName(i), cursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> contentValues.put(cursor.getColumnName(i), cursor.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> contentValues.put(cursor.getColumnName(i), cursor.getBlob(i))
                    }
                }
                result.add(contentValues)
            }
            result
        }
    }

    private fun queryCount(
        table: String,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Int {
        return readableDatabase.query(
            table, arrayOf("COUNT(*)"), selection, selectionArgs, null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun updateTable(
        table: String,
        values: ContentValues,
        whereClause: String,
        whereArgs: Array<String>
    ): Int {
        return writableDatabase.update(table, values, whereClause, whereArgs)
    }

    private fun deleteRows(
        table: String,
        whereClause: String,
        whereArgs: Array<String>
    ): Int {
        return writableDatabase.delete(table, whereClause, whereArgs)
    }

    fun addUser(user: User): Long {
        val values = user.toContentValues().apply {
            put("synced", 0)
        }

        val id = writableDatabase.insert(TABLE_USERS, null, values)

        Log.d("DB_DEBUG", "READ USERS COUNT = " + queryCount(TABLE_USERS))

        return id
    }

    // В DatabaseHelper.kt

    fun getAllUsers(): List<User> {
        val users = mutableListOf<User>()
        val db = readableDatabase
        val cursor = db.query(TABLE_USERS, null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            users.add(cursorToUser(cursor))
        }
        cursor.close()
        return users
    }

    private fun cursorToUser(cursor: Cursor): User {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))          // локальный ID
        val serverId = cursor.getLong(cursor.getColumnIndexOrThrow("server_id")) // если есть
        val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
        val email = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMAIL))
        val password = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD))
        val age = cursor.getInt(cursor.getColumnIndexOrThrow("age"))
        val height = cursor.getInt(cursor.getColumnIndexOrThrow("height"))
        val weight = cursor.getInt(cursor.getColumnIndexOrThrow("current_weight"))
        val targetWeight = cursor.getInt(cursor.getColumnIndexOrThrow("target_weight"))
        val activity = cursor.getString(cursor.getColumnIndexOrThrow("activity_level"))
        val goal = cursor.getString(cursor.getColumnIndexOrThrow("goal"))
        val gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"))
        val dailyCaloriesGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_calories_goal"))
        val dailyProteinGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_protein_goal"))
        val dailyFatGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_fat_goal"))
        val dailyCarbsGoal = cursor.getInt(cursor.getColumnIndexOrThrow("daily_carbs_goal"))

        return User(
            id = id,                     // локальный _id
            name = name,
            email = email,
            password = password,
            age = age,
            height = height,
            weight = weight,
            targetWeight = targetWeight,
            activity = activity,
            goal = goal,
            gender = gender,
            dailyCaloriesGoal = dailyCaloriesGoal,
            dailyProteinGoal = dailyProteinGoal,
            dailyFatGoal = dailyFatGoal,
            dailyCarbsGoal = dailyCarbsGoal
        )
    }

    // В getUserById:
    // В DatabaseHelper.kt исправьте getUserById:
    fun getUserById(localId: Long): User? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM users WHERE _id = ?",
            arrayOf(localId.toString())
        )

        return cursor.use {
            if (it.moveToNext()) cursorToUser(it) else null
        }
    }

    // В DatabaseHelper.kt добавьте:
    fun getUserServerId(localUserId: Long): Long? {
        val cursor = readableDatabase.rawQuery(
            "SELECT server_id FROM $TABLE_USERS WHERE $COL_ID = ?",
            arrayOf(localUserId.toString())
        )
        return cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }

    fun updateUserServerId(localUserId: Long, serverId: Long) {
        writableDatabase.update(
            TABLE_USERS,
            ContentValues().apply { put("server_id", serverId) },
            "$COL_ID = ?",
            arrayOf(localUserId.toString())
        )
    }

    fun getUserByCredentials(email: String, password: String): User? {
        return querySingle(
            table = TABLE_USERS,
            selection = "$COL_EMAIL = ? AND $COL_PASSWORD = ?",
            selectionArgs = arrayOf(email, password)

        )?.toUser()
    }

    fun getUserByEmailAndPassword(email: String, password: String): User? {
        Log.d("DB_DEBUG", "READ USERS COUNT = " + queryCount(TABLE_USERS))

        return readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE $COL_EMAIL = ? AND $COL_PASSWORD = ?",
            arrayOf(email, password)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                User(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)).orEmpty(),
                    email = cursor.getString(cursor.getColumnIndexOrThrow(COL_EMAIL)).orEmpty(),
                    password = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD)).orEmpty(),
                    age = getIntOrNull(cursor, "age"),
                    height = getIntOrNull(cursor, "height"),
                    weight = getIntOrNull(cursor, "current_weight"),
                    targetWeight = getIntOrNull(cursor, "target_weight"),
                    activity = cursor.getString(cursor.getColumnIndexOrThrow("activity_level")).orEmpty(),
                    goal = cursor.getString(cursor.getColumnIndexOrThrow("goal")).orEmpty(),
                    gender = cursor.getString(cursor.getColumnIndexOrThrow("gender")).orEmpty(),
                    registerDate = getLongOrNull(cursor, "register_date"),
                    profileImage = getStringOrNull(cursor, "profile_image"),
                    dailyCaloriesGoal = getIntOrNull(cursor, "daily_calories_goal"),
                    dailyProteinGoal = getIntOrNull(cursor, "daily_protein_goal"),
                    dailyFatGoal = getIntOrNull(cursor, "daily_fat_goal"),
                    dailyCarbsGoal = getIntOrNull(cursor, "daily_carbs_goal")
                )
            } else {
                null
            }
        }
    }

    fun updateUser(user: User) {
        val values = user.toContentValues().apply {
            put("synced", 0)
        }

        writableDatabase.update(
            TABLE_USERS,
            values,
            "$COL_ID = ?",
            arrayOf(user.id.toString())
        )
        Log.d("DB", "USER MARKED UNSYNCED: ${user.id}")
    }

    fun getUserByEmail(email: String): User? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE $COL_EMAIL = ?",
            arrayOf(email)
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToUser(it) else null
        }
    }
    fun addWorkout(userId: Long, name: String): Long {
        return writableDatabase.insert(TABLE_WORKOUTS, null, ContentValues().apply {
            put(COL_USER_ID, userId)
            put(COL_NAME, name)
            put("created_at", System.currentTimeMillis())
            put("synced", 0)
        })
    }

    fun getWorkoutById(workoutId: Long): Workout? {
        return readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_WORKOUTS WHERE $COL_ID = ?",
            arrayOf(workoutId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Workout(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    userId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_USER_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
                )
            } else {
                null
            }
        }
    }

    fun updateWorkout(workoutId: Long, name: String) {
        updateTable(
            table = TABLE_WORKOUTS,
            values = ContentValues().apply { put(COL_NAME, name) },
            whereClause = "$COL_ID = ?",
            whereArgs = arrayOf(workoutId.toString())
        )
    }

    fun getWorkoutsByUser(userId: Long): List<Workout> {
        Log.d("DatabaseHelper", "Getting workouts for userId: $userId")
        Log.d("DB_DEBUG", "READ USERS COUNT = " + queryCount(TABLE_USERS))

        val workouts = queryMultiple(
            table = TABLE_WORKOUTS,
            selection = "$COL_USER_ID = ?",
            selectionArgs = arrayOf(userId.toString())
        ).map { it.toWorkout() }
        Log.d("DatabaseHelper", "Found ${workouts.size} workouts")
        return workouts
    }

    fun addExerciseToWorkout(workoutId: Long, exercise: Exercise): Long {
        var exerciseId = -1L
        writableDatabase.use { db ->
            exerciseId = db.insert(TABLE_EXERCISES, null, ContentValues().apply {
                put(COL_NAME, exercise.name)
            })

            db.insert(TABLE_WORKOUT_EXERCISES, null, ContentValues().apply {
                put("workout_id", workoutId)
                put("exercise_id", exerciseId)
                put("sets", exercise.sets)
                put("reps", exercise.reps)
                put("weight", exercise.weight)
                put("rest", exercise.rest)
                put("synced", 0)
            })
        }
        return exerciseId
    }

    fun getUnsyncedUsers(): List<User> {
        val users = mutableListOf<User>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_USERS WHERE synced = 0", null)

        cursor.use {
            while (it.moveToNext()) {
                users.add(
                    User(
                        id = it.getLong(it.getColumnIndexOrThrow("_id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        email = it.getString(it.getColumnIndexOrThrow("email")),
                        password = it.getString(it.getColumnIndexOrThrow("password")),
                        age = it.getIntOrNull("age"),
                        height = it.getIntOrNull("height"),
                        weight = it.getIntOrNull("current_weight"),
                        targetWeight = it.getIntOrNull("target_weight"),
                        activity = it.getStringOrNull("activity_level"),
                        goal = it.getStringOrNull("goal"),
                        gender = it.getStringOrNull("gender"),
                        registerDate = it.getLongOrNull("register_date"),
                        profileImage = it.getStringOrNull("profile_image"),
                        dailyCaloriesGoal = it.getIntOrNull("daily_calories_goal"),
                        dailyProteinGoal = it.getIntOrNull("daily_protein_goal"),
                        dailyFatGoal = it.getIntOrNull("daily_fat_goal"),
                        dailyCarbsGoal = it.getIntOrNull("daily_carbs_goal")
                    )
                )
            }
        }
        return users
    }

    fun getExercises(workoutId: Long): List<Exercise> {
        return readableDatabase.rawQuery("""
            SELECT we.$COL_ID, e.$COL_NAME, we.sets, we.reps, we.weight, we.rest
            FROM $TABLE_WORKOUT_EXERCISES we
            JOIN $TABLE_EXERCISES e ON e.$COL_ID = we.exercise_id
            WHERE we.workout_id = ?
        """, arrayOf(workoutId.toString())).use { cursor ->
            val exercises = mutableListOf<Exercise>()
            while (cursor.moveToNext()) {
                exercises.add(Exercise(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    workoutId = workoutId,
                    name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                    sets = cursor.getInt(cursor.getColumnIndexOrThrow("sets")),
                    reps = cursor.getInt(cursor.getColumnIndexOrThrow("reps")),
                    weight = cursor.getInt(cursor.getColumnIndexOrThrow("weight")),
                    rest = cursor.getInt(cursor.getColumnIndexOrThrow("rest"))
                ))
            }
            exercises
        }
    }

    fun deleteExercisesByWorkout(workoutId: Long) {
        deleteRows(
            table = TABLE_WORKOUT_EXERCISES,
            whereClause = "workout_id = ?",
            whereArgs = arrayOf(workoutId.toString())
        )
    }

    suspend fun insertOrGetProduct(product: Product): Long = withContext(Dispatchers.IO) {
        getProductByName(product.name)?.id ?: run {
            writableDatabase.insert(TABLE_PRODUCTS, null, product.toContentValues())
        }
    }

    suspend fun getProductByName(name: String): Product? = withContext(Dispatchers.IO) {
        querySingle(
            table = TABLE_PRODUCTS,
            selection = "$COL_NAME = ?",
            selectionArgs = arrayOf(name)
        )?.toProduct()
    }

    suspend fun getProductById(productId: Long): Product? = withContext(Dispatchers.IO) {
        querySingle(
            table = TABLE_PRODUCTS,
            selection = "$COL_ID = ?",
            selectionArgs = arrayOf(productId.toString())
        )?.toProduct()
    }
    fun insertExerciseFromServer(dto: WorkoutExerciseDto) {
        val db = writableDatabase

        val exerciseId = db.insert("exercises", null, ContentValues().apply {
            put("name", dto.name)
        })

        db.insert("workout_exercises", null, ContentValues().apply {
            put("workout_id", dto.workoutId)
            put("exercise_id", exerciseId)
            put("sets", dto.sets)
            put("reps", dto.reps)
            put("weight", dto.weight)
            put("rest", dto.rest)
            put("synced", 1)
        })
    }

    fun getUnsyncedWorkoutExercises(): List<Exercise> {
        return readableDatabase.rawQuery("""
        SELECT we._id, we.workout_id, e.name, we.sets, we.reps, we.weight, we.rest
        FROM workout_exercises we
        JOIN exercises e ON e._id = we.exercise_id
        WHERE we.synced = 0
    """, null).use { cursor ->

            val list = mutableListOf<Exercise>()

            while (cursor.moveToNext()) {
                list.add(
                    Exercise(
                        id = cursor.getLong(0),
                        workoutId = cursor.getLong(1),
                        name = cursor.getString(2),
                        sets = cursor.getInt(3),
                        reps = cursor.getInt(4),
                        weight = cursor.getInt(5),
                        rest = cursor.getInt(6)
                    )
                )
            }
            list
        }
    }
    fun markExerciseSynced(id: Long) {
        writableDatabase.update(
            "workout_exercises",
            ContentValues().apply { put("synced", 1) },
            "_id = ?",
            arrayOf(id.toString())
        )
    }

    suspend fun getAllProductsMatching(query: String): List<Product> = withContext(Dispatchers.IO) {
        Log.d("DB", "Searching products for query: '$query'")

        // 1. Сначала ищем в локальной БД
        val localResults = searchInLocalDatabaseFast(query)
        Log.d("DB", "Local results: ${localResults.size} products")

        // 2. Если нашли достаточно результатов, возвращаем их
        if (localResults.size >= 5) {
            Log.d("DB", "Returning ${localResults.size} local results")
            return@withContext localResults.take(20)
        }

        // 3. Если запрос достаточно длинный и локальных результатов мало, ищем в API
        if (query.length >= 2) {
            try {
                Log.d("DB", "Querying API for: '$query'")
                val apiProducts = ApiManager.searchProducts(query)
                Log.d("DB", "API returned: ${apiProducts.size} products")

                // 4. Фильтруем API продукты, чтобы избежать дубликатов
                val newApiProducts = apiProducts.filter { apiProduct ->
                    localResults.none { localProduct ->
                        localProduct.name.equals(apiProduct.name, ignoreCase = true) ||
                                localProduct.name.contains(apiProduct.name, ignoreCase = true) ||
                                apiProduct.name.contains(localProduct.name, ignoreCase = true)
                    }
                }

                Log.d("DB", "New unique API products: ${newApiProducts.size}")

                // 5. Сохраняем новые продукты в БД
                if (newApiProducts.isNotEmpty()) {
                    saveProductsToDatabase(newApiProducts)
                    Log.d("DB", "Saved ${newApiProducts.size} new products to database")
                }

                // 6. Объединяем результаты
                val combinedResults = localResults + newApiProducts
                Log.d("DB", "Total combined results: ${combinedResults.size} products")

                return@withContext combinedResults.take(20)

            } catch (e: Exception) {
                Log.e("DB", "API search error: ${e.message}")
                // Возвращаем только локальные результаты в случае ошибки
                return@withContext localResults.take(20)
            }
        }

        // Если запрос слишком короткий, возвращаем только локальные результаты
        return@withContext localResults.take(20)
    }

    private fun searchInLocalDatabaseFast(query: String): List<Product> {
        return readableDatabase.rawQuery("""
        SELECT * FROM $TABLE_PRODUCTS 
        WHERE $COL_NAME LIKE ? || '%' COLLATE NOCASE
        OR $COL_NAME LIKE '%' || ? || '%' COLLATE NOCASE
        ORDER BY $COL_NAME ASC
        LIMIT 15
    """, arrayOf(query, query)).use { cursor ->
            val products = mutableListOf<Product>()
            while (cursor.moveToNext()) {
                products.add(
                    Product(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        calories = cursor.getFloat(cursor.getColumnIndexOrThrow("calories")),
                        protein = cursor.getFloat(cursor.getColumnIndexOrThrow("protein")),
                        fat = cursor.getFloat(cursor.getColumnIndexOrThrow("fat")),
                        carbs = cursor.getFloat(cursor.getColumnIndexOrThrow("carbs"))
                    )
                )
            }
            products
        }
    }

    fun markUserSynced(userId: Long) {
        updateTable(
            TABLE_USERS,
            ContentValues().apply { put("synced", 1) },
            "$COL_ID = ?",
            arrayOf(userId.toString())
        )
    }

    // Аналогично для Workouts
    fun getUnsyncedWorkouts(): List<Workout> {
        return queryMultiple(TABLE_WORKOUTS, "synced = 0", null).map { it.toWorkout() }
    }

    fun markWorkoutSynced(workoutId: Long) {
        updateTable(TABLE_WORKOUTS, ContentValues().apply { put("synced", 1) }, "$COL_ID = ?", arrayOf(workoutId.toString()))
    }

    // Для приёмов пищи
    fun getUnsyncedMeals(): List<Meal> {
        // Здесь Meal должен содержать все поля, включая userId, productId и т.д.
        return queryMultiple(TABLE_NUTRITION_LOG, "synced = 0", null).map { it.toMeal() }
    }

    fun markMealSynced(mealId: Long) {
        updateTable(TABLE_NUTRITION_LOG, ContentValues().apply { put("synced", 1) }, "$COL_ID = ?", arrayOf(mealId.toString()))
    }

// В класс DatabaseHelper добавьте:

    fun deleteWorkout(workoutId: Long): Boolean {
        return writableDatabase.use { db ->
            try {
                db.beginTransaction()

                // 1. Удаляем упражнения тренировки
                db.delete(
                    TABLE_WORKOUT_EXERCISES,
                    "workout_id = ?",
                    arrayOf(workoutId.toString())
                )

                // 2. Удаляем саму тренировку
                val rowsDeleted = db.delete(
                    TABLE_WORKOUTS,
                    "$COL_ID = ?",
                    arrayOf(workoutId.toString())
                )

                db.setTransactionSuccessful()
                rowsDeleted > 0
            } catch (e: Exception) {
                Log.e("DB", "Error deleting workout", e)
                false
            } finally {
                db.endTransaction()
            }
        }
    }
    private fun saveProductsToDatabase(products: List<Product>) {
        products.forEach { product ->
            try {
                val exists = queryCount(
                    table = TABLE_PRODUCTS,
                    selection = "$COL_NAME = ? COLLATE NOCASE",
                    selectionArgs = arrayOf(product.name)
                ) > 0

                if (!exists) {
                    writableDatabase.insert(TABLE_PRODUCTS, null, product.toContentValues())
                }
            } catch (e: Exception) {
                Log.e("DB", "Error saving product: ${e.message}")
            }
        }
    }
    suspend fun addMeal(
        userId: Long,
        productId: Long,
        mealType: String,
        quantity: Int,
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float
    ): Long = withContext(Dispatchers.IO) {
        val id = writableDatabase.insert(TABLE_NUTRITION_LOG, null, ContentValues().apply {
            put(COL_USER_ID, userId)
            put(COL_PRODUCT_ID, productId)
            put("meal_type", mealType)
            put("quantity", quantity)
            put("calories", calories)
            put("protein", protein)
            put("fat", fat)
            put("carbs", carbs)
            put("date", System.currentTimeMillis())
            put("synced", 0)
        })

        // Отправляем на сервер
        val mealDto = MealDto(
            id = 0,  // или не передавайте, если сделали id = 0 по умолчанию
            userId = userId,
            productId = productId,
            quantity = quantity,
            calories = calories.toInt(),
            protein = protein.toInt(),
            fat = fat.toInt(),
            carbs = carbs.toInt(),
            mealType = mealType,
            date = System.currentTimeMillis()
        )
        val success = ApiManager.addMeal(mealDto)
        if (!success) {
            Log.e("DatabaseHelper", "Failed to sync meal to server, but saved locally")
            // Здесь можно пометить запись как несинхронизированную (если добавите колонку synced)
        }
        return@withContext id
    }
    fun syncExercises() {
        val exercises = getUnsyncedWorkoutExercises()

        exercises.forEach { ex ->
            CoroutineScope(Dispatchers.IO).launch {
                val success = ApiManager.addExercise(
                    WorkoutExerciseDto(
                        workoutId = ex.workoutId,
                        name = ex.name,
                        sets = ex.sets,
                        reps = ex.reps,
                        weight = ex.weight,
                        rest = ex.rest
                    )
                )

                if (success) {
                    markExerciseSynced(ex.id)
                }
            }
        }
    }

    suspend fun getMealsByUserAndType(userId: Long, mealType: String): List<Meal> =
        withContext(Dispatchers.IO) {
            readableDatabase.rawQuery("""
            SELECT nl.$COL_ID, p.$COL_NAME, nl.quantity, nl.calories, nl.protein, 
                   nl.fat, nl.carbs, nl.date, nl.meal_type, nl.$COL_PRODUCT_ID
            FROM $TABLE_NUTRITION_LOG nl
            JOIN $TABLE_PRODUCTS p ON p.$COL_ID = nl.$COL_PRODUCT_ID
            WHERE nl.$COL_USER_ID = ? AND nl.meal_type = ? COLLATE NOCASE
            ORDER BY nl.date ASC
        """, arrayOf(userId.toString(), mealType)).use { cursor ->
                val meals = mutableListOf<Meal>()
                while (cursor.moveToNext()) {
                    meals.add(Meal(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        userId = userId,
                        productId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)),
                        productName = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                        calories = cursor.getDouble(cursor.getColumnIndexOrThrow("calories")).toInt(),
                        protein = cursor.getDouble(cursor.getColumnIndexOrThrow("protein")).toInt(),
                        fat = cursor.getDouble(cursor.getColumnIndexOrThrow("fat")).toInt(),
                        carbs = cursor.getDouble(cursor.getColumnIndexOrThrow("carbs")).toInt(),
                        date = cursor.getLong(cursor.getColumnIndexOrThrow("date")),
                        mealType = cursor.getString(cursor.getColumnIndexOrThrow("meal_type"))
                    ))
                }
                meals
            }
        }
    suspend fun getMealSummaryByTypeAndDate(userId: Long, mealType: String, date: Long): MealSummary? =
        withContext(Dispatchers.IO) {
            val startOfDay = getStartOfDay(date)
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1 // Конец дня (23:59:59.999)

            readableDatabase.rawQuery("""
            SELECT 
                meal_type,
                COUNT($COL_ID) as product_count,
                SUM(quantity) as total_quantity,
                SUM(calories) as total_calories,
                SUM(protein) as total_protein,
                SUM(fat) as total_fat,
                SUM(carbs) as total_carbs
            FROM $TABLE_NUTRITION_LOG
            WHERE $COL_USER_ID = ? 
                AND meal_type = ? COLLATE NOCASE
                AND date BETWEEN ? AND ?
            GROUP BY meal_type
        """, arrayOf(
                userId.toString(),
                mealType,
                startOfDay.toString(),
                endOfDay.toString()
            )).use { cursor ->
                if (cursor.moveToFirst()) {
                    MealSummary(
                        mealType = cursor.getString(cursor.getColumnIndexOrThrow("meal_type")),
                        productCount = cursor.getInt(cursor.getColumnIndexOrThrow("product_count")),
                        totalQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("total_quantity")),
                        totalCalories = cursor.getDouble(cursor.getColumnIndexOrThrow("total_calories")).toInt(),
                        totalProtein = cursor.getDouble(cursor.getColumnIndexOrThrow("total_protein")).toInt(),
                        totalFat = cursor.getDouble(cursor.getColumnIndexOrThrow("total_fat")).toInt(),
                        totalCarbs = cursor.getDouble(cursor.getColumnIndexOrThrow("total_carbs")).toInt()
                    )
                } else {
                    null
                }
            }
        }
    fun userExists(id: Long): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT _id FROM $TABLE_USERS WHERE _id = ?",
            arrayOf(id.toString())
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }
    suspend fun getMealProductsByTypeAndDate(userId: Long, mealType: String, date: Long): List<MealProductDisplay> =
        withContext(Dispatchers.IO) {
            val startOfDay = getStartOfDay(date)
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1

            readableDatabase.rawQuery("""
            SELECT 
                nl.$COL_ID,
                nl.$COL_PRODUCT_ID,
                p.$COL_NAME,
                nl.quantity,
                nl.calories,
                nl.protein,
                nl.fat,
                nl.carbs
            FROM $TABLE_NUTRITION_LOG nl
            JOIN $TABLE_PRODUCTS p ON p.$COL_ID = nl.$COL_PRODUCT_ID
            WHERE nl.$COL_USER_ID = ? 
                AND nl.meal_type = ? COLLATE NOCASE
                AND nl.date BETWEEN ? AND ?
            ORDER BY nl.date ASC
        """, arrayOf(
                userId.toString(),
                mealType,
                startOfDay.toString(),
                endOfDay.toString()
            )).use { cursor ->
                val mealProducts = mutableListOf<MealProductDisplay>()
                while (cursor.moveToNext()) {
                    mealProducts.add(
                        MealProductDisplay(
                            mealItemId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                            productId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)),
                            productName = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                            quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                            calories = cursor.getDouble(cursor.getColumnIndexOrThrow("calories")).toFloat(),
                            protein = cursor.getDouble(cursor.getColumnIndexOrThrow("protein")).toFloat(),
                            fat = cursor.getDouble(cursor.getColumnIndexOrThrow("fat")).toFloat(),
                            carbs = cursor.getDouble(cursor.getColumnIndexOrThrow("carbs")).toFloat()
                        )
                    )
                }
                mealProducts
            }
        }

    private fun getStartOfDay(date: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    suspend fun getMealProductsByType(userId: Long, mealType: String): List<MealProductDisplay> =
        withContext(Dispatchers.IO) {
            readableDatabase.rawQuery("""
                SELECT nl.$COL_ID, nl.$COL_PRODUCT_ID, p.$COL_NAME, nl.quantity, 
                       nl.calories, nl.protein, nl.fat, nl.carbs
                FROM $TABLE_NUTRITION_LOG nl
                JOIN $TABLE_PRODUCTS p ON p.$COL_ID = nl.$COL_PRODUCT_ID
                WHERE nl.$COL_USER_ID = ? AND nl.meal_type = ? COLLATE NOCASE
                ORDER BY nl.date ASC
            """, arrayOf(userId.toString(), mealType)).use { cursor ->
                val mealProducts = mutableListOf<MealProductDisplay>()
                while (cursor.moveToNext()) {
                    mealProducts.add(MealProductDisplay(
                        mealItemId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        productId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)),
                        productName = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                        calories = cursor.getDouble(cursor.getColumnIndexOrThrow("calories")).toFloat(),
                        protein = cursor.getDouble(cursor.getColumnIndexOrThrow("protein")).toFloat(),
                        fat = cursor.getDouble(cursor.getColumnIndexOrThrow("fat")).toFloat(),
                        carbs = cursor.getDouble(cursor.getColumnIndexOrThrow("carbs")).toFloat()
                    ))
                }
                mealProducts
            }
        }

    suspend fun deleteMealItem(mealItemId: Long): Boolean = withContext(Dispatchers.IO) {
        deleteRows(
            table = TABLE_NUTRITION_LOG,
            whereClause = "$COL_ID = ?",
            whereArgs = arrayOf(mealItemId.toString())
        ) > 0
    }
    fun updateUserFromServer(userDto: UserDto) {
        val existingUser = getUserByEmail(userDto.email)

        val values = ContentValues().apply {
            put("server_id", userDto.id)  // ВАЖНО: сохраняем server_id
            put(COL_NAME, userDto.name)
            put(COL_EMAIL, userDto.email)
            if (!userDto.password.isNullOrEmpty()) {
                put(COL_PASSWORD, userDto.password)
            } else {
                put(COL_PASSWORD, existingUser?.password)
            }
            put("age", userDto.age)
            put("height", userDto.height)
            put("current_weight", userDto.weight)
            put("target_weight", userDto.targetWeight)
            put("activity_level", userDto.activity)
            put("goal", userDto.goal)
            put("gender", userDto.gender)
            put("daily_calories_goal", userDto.dailyCaloriesGoal)
            put("daily_protein_goal", userDto.dailyProteinGoal)
            put("daily_fat_goal", userDto.dailyFatGoal)
            put("daily_carbs_goal", userDto.dailyCarbsGoal)
            put("synced", 1)
        }

        val updated = writableDatabase.update(
            TABLE_USERS,
            values,
            "$COL_EMAIL = ?",
            arrayOf(userDto.email)
        )

        Log.d("DB", "updateUserFromServer: updated=$updated rows for email=${userDto.email}")

        // Если не нашли по email, пробуем по server_id
        if (updated == 0) {
            writableDatabase.update(
                TABLE_USERS,
                values,
                "server_id = ?",
                arrayOf(userDto.id.toString())
            )
        }
    }

    fun clearWorkouts() {
        writableDatabase.delete(TABLE_WORKOUTS, null, null)
    }
    fun clearMeals() {
        writableDatabase.delete(TABLE_NUTRITION_LOG, null, null)
    }
    fun clearExercises() {
        writableDatabase.delete(TABLE_EXERCISES, null, null)
    }
    suspend fun updateMealItem(
        mealItemId: Long,
        quantity: Int,
        calories: Float,
        protein: Float,
        fat: Float,
        carbs: Float
    ): Boolean = withContext(Dispatchers.IO) {
        updateTable(
            table = TABLE_NUTRITION_LOG,
            values = ContentValues().apply {
                put("quantity", quantity)
                put("calories", calories)
                put("protein", protein)
                put("fat", fat)
                put("carbs", carbs)
            },
            whereClause = "$COL_ID = ?",
            whereArgs = arrayOf(mealItemId.toString())
        ) > 0
    }
    fun updateUserMacros(
        userId: Long,
        calories: Int?,
        protein: Int?,
        fat: Int?,
        carbs: Int?
    ) {
        val db = writableDatabase

        val values = ContentValues().apply {
            put("dailycaloriesgoal", calories)
            put("dailyproteingoal", protein)
            put("dailyfatgoal", fat)
            put("dailycarbsgoal", carbs)
        }

        db.update(
            "users",
            values,
            "userid = ?",
            arrayOf(userId.toString())
        )
    }
    suspend fun getDailySummary(userId: Long): DailySummary? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("""
            SELECT DATE(datetime(date/1000, 'unixepoch', 'localtime')) as log_date,
                   SUM(calories) as total_calories,
                   SUM(protein) as total_protein,
                   SUM(fat) as total_fat,
                   SUM(carbs) as total_carbs
            FROM $TABLE_NUTRITION_LOG
            WHERE $COL_USER_ID = ? 
            AND DATE(datetime(date/1000, 'unixepoch', 'localtime')) = DATE('now', 'localtime')
            GROUP BY log_date
        """, arrayOf(userId.toString())).use { cursor ->
            if (cursor.moveToFirst()) DailySummary(
                date = cursor.getString(cursor.getColumnIndexOrThrow("log_date")),
                totalCalories = cursor.getDouble(cursor.getColumnIndexOrThrow("total_calories")).toInt(),
                totalProtein = cursor.getDouble(cursor.getColumnIndexOrThrow("total_protein")).toInt(),
                totalFat = cursor.getDouble(cursor.getColumnIndexOrThrow("total_fat")).toInt(),
                totalCarbs = cursor.getDouble(cursor.getColumnIndexOrThrow("total_carbs")).toInt()
            ) else null
        }
    }
    fun getWorkoutByServerId(serverId: Long): Workout? {
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM workouts WHERE server_id = ?",
            arrayOf(serverId.toString())
        )

        return if (cursor.moveToFirst()) {
            Workout(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                userId = cursor.getLong(cursor.getColumnIndexOrThrow("user_id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            )
        } else null
    }
    fun updateWorkoutFromServer(workout: WorkoutDto) {
        val db = this.writableDatabase

        val values = ContentValues().apply {
            put("name", workout.name)
            put("user_id", workout.userId)
        }

        db.update(
            "workouts",
            values,
            "server_id = ?",
            arrayOf(workout.id.toString())
        )
    }

    suspend fun getMealSummaryByType(userId: Long, mealType: String): MealSummary? =
        withContext(Dispatchers.IO) {
            readableDatabase.rawQuery("""
                SELECT meal_type,
                       COUNT($COL_ID) as product_count,
                       SUM(quantity) as total_quantity,
                       SUM(calories) as total_calories,
                       SUM(protein) as total_protein,
                       SUM(fat) as total_fat,
                       SUM(carbs) as total_carbs
                FROM $TABLE_NUTRITION_LOG
                WHERE $COL_USER_ID = ? AND meal_type = ? COLLATE NOCASE
                GROUP BY meal_type
            """, arrayOf(userId.toString(), mealType)).use { cursor ->
                if (cursor.moveToFirst()) MealSummary(
                    mealType = cursor.getString(cursor.getColumnIndexOrThrow("meal_type")),
                    productCount = cursor.getInt(cursor.getColumnIndexOrThrow("product_count")),
                    totalQuantity = cursor.getInt(cursor.getColumnIndexOrThrow("total_quantity")),
                    totalCalories = cursor.getDouble(cursor.getColumnIndexOrThrow("total_calories")).toInt(),
                    totalProtein = cursor.getDouble(cursor.getColumnIndexOrThrow("total_protein")).toInt(),
                    totalFat = cursor.getDouble(cursor.getColumnIndexOrThrow("total_fat")).toInt(),
                    totalCarbs = cursor.getDouble(cursor.getColumnIndexOrThrow("total_carbs")).toInt()
                ) else null
            }
        }

    suspend fun archiveOldMeals(userId: Long): Boolean = withContext(Dispatchers.IO) {
        val todayStart = getStartOfDay()

        val oldMeals = readableDatabase.rawQuery("""
            SELECT nl.*, p.$COL_NAME as product_name
            FROM $TABLE_NUTRITION_LOG nl
            JOIN $TABLE_PRODUCTS p ON p.$COL_ID = nl.$COL_PRODUCT_ID
            WHERE nl.$COL_USER_ID = ? AND nl.date < ?
        """, arrayOf(userId.toString(), todayStart.toString())).use { cursor ->
            val meals = mutableListOf<Pair<MealProductDisplay, Long>>()
            while (cursor.moveToNext()) {
                val meal = MealProductDisplay(
                    mealItemId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    productId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)),
                    productName = cursor.getString(cursor.getColumnIndexOrThrow("product_name")),
                    quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                    calories = cursor.getDouble(cursor.getColumnIndexOrThrow("calories")).toFloat(),
                    protein = cursor.getDouble(cursor.getColumnIndexOrThrow("protein")).toFloat(),
                    fat = cursor.getDouble(cursor.getColumnIndexOrThrow("fat")).toFloat(),
                    carbs = cursor.getDouble(cursor.getColumnIndexOrThrow("carbs")).toFloat()
                )
                val date = cursor.getLong(cursor.getColumnIndexOrThrow("date"))
                meals.add(Pair(meal, date))
            }
            meals
        }

        if (oldMeals.isEmpty()) return@withContext true

        writableDatabase.use { db ->
            db.beginTransaction()
            try {
                oldMeals.forEach { (mealData, originalDate) ->
                    val values = ContentValues().apply {
                        put(COL_USER_ID, userId)
                        put(COL_PRODUCT_ID, mealData.productId)
                        put("product_name", mealData.productName)
                        put("meal_type", "Завтрак")
                        put("quantity", mealData.quantity)
                        put("calories", mealData.calories)
                        put("protein", mealData.protein)
                        put("fat", mealData.fat)
                        put("carbs", mealData.carbs)
                        put("date", originalDate)
                        put("archive_date", System.currentTimeMillis())
                    }
                    db.insert(TABLE_NUTRITION_ARCHIVE, null, values)
                }

                val rowsDeleted = db.delete(
                    TABLE_NUTRITION_LOG,
                    "$COL_USER_ID = ? AND date < ?",
                    arrayOf(userId.toString(), todayStart.toString())
                )

                db.setTransactionSuccessful()
                rowsDeleted > 0
            } catch (e: Exception) {
                Log.e("DB", "Archive error", e)
                false
            } finally {
                db.endTransaction()
            }
        }
    }

    suspend fun clearTodayMeals(userId: Long): Boolean = withContext(Dispatchers.IO) {
        val todayStart = getStartOfDay()
        deleteRows(
            table = TABLE_NUTRITION_LOG,
            whereClause = "$COL_USER_ID = ? AND date >= ?",
            whereArgs = arrayOf(userId.toString(), todayStart.toString())
        ) > 0
    }
    // DatabaseHelper.kt

    fun insertWorkoutFromServer(userId: Long, name: String): Long {
        return writableDatabase.insert(TABLE_WORKOUTS, null, ContentValues().apply {
            put(COL_USER_ID, userId)
            put(COL_NAME, name)
            put("synced", 1)       // уже синхронизировано
            put("created_at", System.currentTimeMillis())
        })
    }

    fun insertMealFromServer(mealDto: MealDto): Long {
        return writableDatabase.insert(TABLE_NUTRITION_LOG, null, ContentValues().apply {
            put(COL_USER_ID, mealDto.userId)
            put(COL_PRODUCT_ID, mealDto.productId)
            put("meal_type", mealDto.mealType)
            put("quantity", mealDto.quantity)
            put("calories", mealDto.calories)
            put("protein", mealDto.protein)
            put("fat", mealDto.fat)
            put("carbs", mealDto.carbs)
            put("date", mealDto.date)
            put("synced", 1)
        })
    }

    fun getMealById(mealId: Long): Meal? {
        return querySingle(
            table = TABLE_NUTRITION_LOG,
            selection = "$COL_ID = ?",
            selectionArgs = arrayOf(mealId.toString())
        )?.toMeal()
    }

    // В DatabaseHelper.kt:
    fun insertUserFromServer(userDto: UserDto): Long {
        val db = writableDatabase

        // Сначала проверяем по email
        val existingByEmail = getUserByEmail(userDto.email)
        if (existingByEmail != null) {
            updateUserFromServer(userDto)
            return existingByEmail.id
        }

        // Проверяем по server_id
        val existingByServerId = getUserByServerId(userDto.id)
        if (existingByServerId != null) {
            updateUserFromServer(userDto)
            return existingByServerId.id
        }

        val values = ContentValues().apply {
            put("server_id", userDto.id)
            put(COL_NAME, userDto.name)
            put(COL_EMAIL, userDto.email)
            put(COL_PASSWORD, userDto.password ?: "")
            put("age", userDto.age)
            put("height", userDto.height)
            put("current_weight", userDto.weight)
            put("target_weight", userDto.targetWeight)
            put("activity_level", userDto.activity)
            put("goal", userDto.goal)
            put("gender", userDto.gender)
            put("daily_calories_goal", userDto.dailyCaloriesGoal)
            put("daily_protein_goal", userDto.dailyProteinGoal)
            put("daily_fat_goal", userDto.dailyFatGoal)
            put("daily_carbs_goal", userDto.dailyCarbsGoal)
            put("synced", 1)
        }

        val newId = db.insert(TABLE_USERS, null, values)
        Log.d("DB", "insertUserFromServer: newId=$newId for email=${userDto.email}, serverId=${userDto.id}")
        return newId
    }

    fun getLocalUserIdByEmail(email: String): Long? {
        val cursor = readableDatabase.rawQuery(
            "SELECT _id FROM $TABLE_USERS WHERE $COL_EMAIL = ?",
            arrayOf(email)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }
// В DatabaseHelper.kt добавьте этот метод:

    fun getUserByServerId(serverId: Long): User? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE server_id = ?",
            arrayOf(serverId.toString())
        )

        return cursor.use {
            if (it.moveToFirst()) cursorToUser(it) else null
        }
    }
    private fun getStartOfDay(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}



private fun Cursor.getIntOrNull(columnName: String): Int? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getInt(index) else null
}

private fun Cursor.getLongOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}
private fun ContentValues.toUser(): User {
    return User(
        id = getAsLong("_id") ?: 0L,
        name = getAsString("name") ?: "",
        email = getAsString("email") ?: "",
        password = getAsString("password") ?: "",
        age = getAsInteger("age"),
        height = getAsInteger("height"),
        weight = getAsInteger("current_weight"),
        targetWeight = getAsInteger("target_weight"),
        activity = getAsString("activity_level") ?: "",
        goal = getAsString("goal") ?: "",
        gender = getAsString("gender") ?: "",
        registerDate = getAsLong("register_date"),
        profileImage = getAsString("profile_image"),
        dailyCaloriesGoal = getAsInteger("daily_calories_goal"),
        dailyProteinGoal = getAsInteger("daily_protein_goal"),
        dailyFatGoal = getAsInteger("daily_fat_goal"),
        dailyCarbsGoal = getAsInteger("daily_carbs_goal")
    )
}

private fun User.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("name", name)
        put("email", email)
        put("password", password)
        put("age", age)
        put("height", height)
        put("current_weight", weight)
        put("target_weight", targetWeight)
        put("activity_level", activity)
        put("goal", goal)
        put("gender", gender)
        put("register_date", registerDate ?: System.currentTimeMillis())
        put("profile_image", profileImage)
        put("daily_calories_goal", dailyCaloriesGoal)
        put("daily_protein_goal", dailyProteinGoal)
        put("daily_fat_goal", dailyFatGoal)
        put("daily_carbs_goal", dailyCarbsGoal)
    }
}
private fun ContentValues.toMeal(): Meal {
    return Meal(
        id = getAsLong("_id") ?: 0L,
        userId = getAsLong("user_id") ?: 0L,
        productId = getAsLong("product_id") ?: 0L,
        quantity = getAsInteger("quantity") ?: 0,
        calories = (getAsDouble("calories") ?: 0.0).toInt(),
        protein = (getAsDouble("protein") ?: 0.0).toInt(),
        fat = (getAsDouble("fat") ?: 0.0).toInt(),
        carbs = (getAsDouble("carbs") ?: 0.0).toInt(),
        mealType = getAsString("meal_type") ?: "",
        date = getAsLong("date") ?: 0L,
        productName = ""   // или уберите это поле из конструктора Meal
    )
}
private fun ContentValues.toWorkout(): Workout {
    return Workout(
        id = getAsLong("_id") ?: 0L,
        userId = getAsLong("user_id") ?: 0L,
        name = getAsString("name") ?: ""
    )
}
private fun ContentValues.toProduct(): Product {
    return Product(
        id = getAsLong("_id") ?: 0L,
        name = getAsString("name") ?: "",
        calories = (getAsDouble("calories") ?: 0.0).toFloat(),
        protein = (getAsDouble("protein") ?: 0.0).toFloat(),
        fat = (getAsDouble("fat") ?: 0.0).toFloat(),
        carbs = (getAsDouble("carbs") ?: 0.0).toFloat()
    )
}


private fun Product.toContentValues(): ContentValues {
    return ContentValues().apply {
        put("name", name)
        put("calories", calories)
        put("protein", protein)
        put("fat", fat)
        put("carbs", carbs)
    }
}