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

    /** 分享内容解析结果：有效正文 / 无有效内容 / 超长（TD-020⑤：超长须明确告知，不再笼统"写入失败"）。 */
    private sealed interface ShareContent {
        data class Valid(val text: String) : ShareContent
        object Invalid : ShareContent
        object TooLong : ShareContent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Perform file operations on background thread to prevent ANR
        lifecycleScope.launch(Dispatchers.IO) {
            val fileRepo = FileRepository(this@ShareReceiverActivity)
            val useSAF = fileRepo.isUsingSAF()
            val fileUri = fileRepo.getFileUri()
            val defaultFile = fileRepo.getDefaultFile()

            val resolved = resolveIntentContent(intent)

            var priorDuplicates = 0
            // TD-021：SAF 授权失效的两个信号——写失败 / 自动回退默认（后者须明示，防"数据分家"困惑）
            var safWriteFailed = false
            var fellBackToDefault = false
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
                        fellBackToDefault = true
                    }
                    FileBasedMarkdownFile(this@ShareReceiverActivity, defaultFile)
                }
                if (!markdownFile.exists()) {
                    markdownFile.initHeader()
                }
                if (resolved is ShareContent.Valid) {
                    val appended = markdownFile.append(resolved.text)
                    if (appended) {
                        // 重复内容计数（追加后读，减去刚写入的 1 条）
                        priorDuplicates = EntryParser.parse(markdownFile.readAll())
                            .count { it.content == resolved.text } - 1
                    }
                    if (!appended && safValid) safWriteFailed = true
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
                when {
                    resolved is ShareContent.TooLong ->
                        showToast(this@ShareReceiverActivity, "内容过长（上限 $MAX_CONTENT_LENGTH 字符），未保存")
                    success && fellBackToDefault -> { // TD-021：明示回退，不让数据"无声分家"
                        fileRepo.setFileLastModified(System.currentTimeMillis())
                        showToast(this@ShareReceiverActivity, "已保存到默认文件（自定义目录已失效，可打开 appendo 重选）")
                    }
                    success -> {
                        fileRepo.setFileLastModified(System.currentTimeMillis())
                        val msg = if (dupCount > 0) {
                            "Appendo已收到（已有相同内容 $dupCount 条）"
                        } else {
                            "Appendo已收到"
                        }
                        showToast(this@ShareReceiverActivity, msg)
                    }
                    resolved is ShareContent.Invalid ->
                        showToast(this@ShareReceiverActivity, "未找到可保存的内容")
                    safWriteFailed ->
                        showToast(this@ShareReceiverActivity, "写入失败：自定义目录授权可能已失效，请打开 appendo 重选文件")
                    else ->
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
    private fun resolveIntentContent(intent: Intent): ShareContent {
        // Validate the intent action first
        if (Intent.ACTION_SEND != intent.action) {
            Log.w(TAG, "Invalid intent action: ${intent.action}")
            return ShareContent.Invalid
        }

        // Try to get text content
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            // Validate content length to prevent DOS attacks
            return if (text.length <= MAX_CONTENT_LENGTH) {
                // 2026-09-01 决策①：输入侧不做 trim——用户首尾空白原样写入文件（保真优先）
                ShareContent.Valid(text)
            } else {
                Log.w(TAG, "Content too long: ${text.length} chars")
                ShareContent.TooLong
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
            if (uriString.isBlank()) {
                Log.w(TAG, "Invalid URI content")
                return ShareContent.Invalid
            }
            return if (uriString.length <= MAX_CONTENT_LENGTH) {
                ShareContent.Valid(uriString) // 决策①：不 trim，保真
            } else {
                Log.w(TAG, "URI content too long: ${uriString.length} chars")
                ShareContent.TooLong
            }
        }

        Log.w(TAG, "No valid content found in intent")
        return ShareContent.Invalid
    }
}
