package com.example.fitplan

import android.app.Application
import android.util.Log
import com.example.fitplan.DataBase.DatabaseHelper

class App : Application() {

    companion object {
        lateinit var instance: App
            private set
    }

    lateinit var db: DatabaseHelper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = DatabaseHelper(this)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(
                "APP_CRASH",
                "Crash in thread ${thread.name}",
                throwable
            )
        }
    }
}
