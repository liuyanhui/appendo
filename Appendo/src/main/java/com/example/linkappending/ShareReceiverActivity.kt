package com.example.linkappending

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import com.example.linkappending.data.FileRepository
import com.example.linkappending.util.SafMarkdownFile

class ShareReceiverActivity : ComponentActivity() {

    companion object {
        private const val CHANNEL_ID = "write_feedback"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fileRepo = FileRepository(this)
        val fileUri = fileRepo.getFileUri()

        // Check if file URI exists and is valid
        if (fileUri == null || !fileRepo.isFileUriValid()) {
            // No valid file, show notification to user
            showNoFileNotification()
            finish()
            return
        }

        val markdownFile = SafMarkdownFile(this, fileUri)

        // Initialize file if it doesn't exist
        if (!markdownFile.exists()) {
            markdownFile.initHeader()
        }

        val content = resolveIntentContent(intent)
        val success = if (content != null) {
            markdownFile.append(content)
        } else {
            false
        }

        if (success) {
            // Update file last modified timestamp
            fileRepo.setFileLastModified(System.currentTimeMillis())
            showNotification()
        } else {
            Toast.makeText(this, "写入失败", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun resolveIntentContent(intent: Intent): String? {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
            }
            else -> null
        }
    }

    private fun showNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(getString(R.string.notification_title))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showNoFileNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.no_file_hint))
            .setContentText("请先打开 Appendo 选择文件")
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
