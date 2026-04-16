package com.yiyue31.android.appendo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application class for one-time initialization.
 */
class AppendoApplication : Application() {

    companion object {
        private const val CHANNEL_ID = "write_feedback"
        private const val CHANNEL_NAME = "写入反馈"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Create notification channel once at app startup.
     * This prevents duplicate channel warnings and improves efficiency.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "通知内容已成功写入文件"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
