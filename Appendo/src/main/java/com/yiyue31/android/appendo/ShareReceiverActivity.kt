package com.yiyue31.android.appendo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import com.yiyue31.android.appendo.util.SafMarkdownFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShareReceiverActivity"
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

            var priorDuplicates = 0
            // Try to write content
            val success = try {
                // Check if SAF mode is still valid
                val safValid = useSAF && fileUri != null && fileRepo.isFileUriValid()
                val markdownFile = if (safValid) {
                    SafMarkdownFile(this@ShareReceiverActivity, fileUri!!)
                } else {
                    // SAF URI 无效 → 回退默认文件
                    if (useSAF && fileUri != null) {
                        Log.w(TAG, "SAF URI invalid, falling back to default file")
                        fileRepo.clearFileUri()
                    }
                    FileBasedMarkdownFile(this@ShareReceiverActivity, defaultFile)
                }
                if (!markdownFile.exists()) {
                    markdownFile.initHeader()
                }
                if (content != null) {
                    val appended = markdownFile.append(content)
                    if (appended) {
                        // 重复内容计数（追加后读，减去刚写入的 1 条）
                        priorDuplicates = EntryParser.parse(markdownFile.readAll())
                            .count { it.content == content } - 1
                    }
                    appended
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append content", e)
                false
            }

            val dupCount = priorDuplicates
            // Update UI on main thread
            withContext(Dispatchers.Main) {
                if (success) {
                    fileRepo.setFileLastModified(System.currentTimeMillis())
                    val msg = if (dupCount > 0) {
                        "Appendo已收到（已有相同内容 $dupCount 条）"
                    } else {
                        "Appendo已收到"
                    }
                    showToast(this@ShareReceiverActivity, msg)
                } else {
                    showToast(this@ShareReceiverActivity, "写入失败")
                }
                // Delay finish to allow toast to be shown
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    finish()
                }, 500)
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
}
