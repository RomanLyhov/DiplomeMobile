package com.example.fitplan.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.*

object NotificationScheduler {

    fun scheduleNotification(
        context: Context,
        workoutName: String,
        triggerTime: Long,
        title: String,
        message: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val notificationId = (workoutName + triggerTime).hashCode()

        // Логируем планирование
        Log.d("NOTIFICATION", "=== SCHEDULING ===")
        Log.d("NOTIFICATION", "Title: $title")
        Log.d("NOTIFICATION", "Trigger time: ${Date(triggerTime)}")
        Log.d("NOTIFICATION", "Current time: ${Date(System.currentTimeMillis())}")
        Log.d("NOTIFICATION", "Delay: ${(triggerTime - System.currentTimeMillis()) / 1000} seconds")
        Log.d("NOTIFICATION", "Notification ID: $notificationId")

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", message)
            putExtra("workoutName", workoutName)
            putExtra("notificationId", notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d("NOTIFICATION", "✅ Scheduled with setExactAndAllowWhileIdle")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d("NOTIFICATION", "✅ Scheduled with setExact")
            }
        } catch (e: Exception) {
            Log.e("NOTIFICATION", "❌ Failed to schedule: ${e.message}")
        }
    }
}