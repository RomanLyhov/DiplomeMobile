package com.example.fitplan.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NOTIFICATION", "=== RECEIVER TRIGGERED ===")

        val title = intent.getStringExtra("title") ?: "Напоминание"
        val message = intent.getStringExtra("message") ?: "У вас запланирована тренировка"
        val notificationId = intent.getIntExtra("notificationId", 0)

        Log.d("NOTIFICATION", "Title: $title")
        Log.d("NOTIFICATION", "Message: $message")
        Log.d("NOTIFICATION", "Notification ID: $notificationId")

        // Создаем канал для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "workout_channel",
                "Тренировки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Напоминания о тренировках"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d("NOTIFICATION", "✅ Notification channel created")
        }

        val notification = NotificationCompat.Builder(context, "workout_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
        Log.d("NOTIFICATION", "✅ Notification shown with ID: $notificationId")
    }
}