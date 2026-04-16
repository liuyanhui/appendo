package com.yiyue31.android.appendo

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import com.yiyue31.android.appendo.util.SafMarkdownFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareReceiverActivity"
        private const val CHANNEL_ID = "write_feedback"
        private const val NOTIFICATION_ID = 1
        private const val MAX_CONTENT_LENGTH = 10000 // Prevent DOS attacks
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Perform file operations on background thread to prevent ANR
        lifecycleScope.launch(Dispatchers.IO) {
            val fileRepo = FileRepository(this@ShareReceiverActivity)
            val useSAF = fileRepo.isUsingSAF()
            val fileUri = fileRepo.getFileUri()
            val defaultFile = fileRepo.getDefaultFile()

            val content = resolveIntentContent(intent)

            // Try to write content
            val success = try {
                // Check if SAF mode is still valid
                val safValid = useSAF && fileUri != null && fileRepo.isFileUriValid()

                if (safValid) {
                    // Use SAF mode
                    val markdownFile = SafMarkdownFile(this@ShareReceiverActivity, fileUri!!)

                    // Initialize file if it doesn't exist
                    if (!markdownFile.exists()) {
                        markdownFile.initHeader()
                    }

                    if (content != null) {
                        markdownFile.append(content)
                    } else {
                        false
                    }
                } else {
                    // SAF mode but URI is invalid - fallback to default file
                    if (useSAF && fileUri != null) {
                        Log.w(TAG, "SAF URI invalid, falling back to default file")
                        fileRepo.clearFileUri()
                    }

                    // Use default file mode
                    val markdownFile = FileBasedMarkdownFile(this@ShareReceiverActivity, defaultFile)

                    // Initialize file if it doesn't exist
                    if (!markdownFile.exists()) {
                        markdownFile.initHeader()
                    }

                    if (content != null) {
                        markdownFile.append(content)
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append content", e)
                false
            }

            // Update UI on main thread
            withContext(Dispatchers.Main) {
                if (success) {
                    // Update file last modified timestamp
                    fileRepo.setFileLastModified(System.currentTimeMillis())
                    showNotification()
                } else {
                    Toast.makeText(this@ShareReceiverActivity, "写入失败", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
    }

    /**
     * Resolve and validate content from intent.
     * Includes security validation to prevent malicious content injection.
     */
    private fun resolveIntentContent(intent: Intent): String? {
        // Validate the intent action first
        if (Intent.ACTION_SEND != intent.action) {
            Log.w(TAG, "Invalid intent action: ${intent.action}")
            return null
        }

        // Try to get text content
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            // Validate content length to prevent DOS attacks
            return if (text.length <= MAX_CONTENT_LENGTH) {
                text.trim()
            } else {
                Log.w(TAG, "Content too long: ${text.length} chars")
                null
            }
        }

        // Try to get URI content
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }

        // Validate and convert URI to string
        if (uri != null) {
            val uriString = uri.toString()
            return if (uriString.isNotBlank() && uriString.length <= MAX_CONTENT_LENGTH) {
                uriString.trim()
            } else {
                Log.w(TAG, "Invalid URI content")
                null
            }
        }

        Log.w(TAG, "No valid content found in intent")
        return null
    }

    private fun showNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(getString(R.string.notification_title))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
