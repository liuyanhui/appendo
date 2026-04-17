package com.yiyue31.android.appendo.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yiyue31.android.appendo.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader

class SafMarkdownFile(
    private val context: Context,
    private val uri: Uri
) : MarkdownFileOperations {
    private val lock = Any()

    companion object {
        private const val TAG = "SafMarkdownFile"
    }

    override fun append(content: String): Boolean {
        synchronized(lock) {
            return try {
                val entry = MarkdownFormatter.formatEntry(content)
                val outputStream = context.contentResolver.openOutputStream(uri, "wa")
                if (outputStream != null) {
                    outputStream.use { output ->
                        output.write(entry.toByteArray(Charsets.UTF_8))
                    }
                    true
                } else {
                    // Fallback: read all content and rewrite
                    appendFallback(content)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append content, trying fallback", e)
                // Fallback on any error
                appendFallback(content)
            }
        }
    }

    private fun appendFallback(content: String): Boolean {
        return try {
            val existingContent = readAll()
            val entry = MarkdownFormatter.formatEntry(content)
            val newContent = if (existingContent.isBlank()) {
                MarkdownFormatter.FILE_HEADER + entry
            } else {
                existingContent + entry
            }

            val outputStream = context.contentResolver.openOutputStream(uri, "wt")
            if (outputStream != null) {
                outputStream.use { output ->
                    output.write(newContent.toByteArray(Charsets.UTF_8))
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append fallback content", e)
            false
        }
    }

    override fun readAll(): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                inputStream.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            ""
        }
    }

    override fun clear(): Boolean {
        synchronized(lock) {
            return try {
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream != null) {
                    outputStream.use { output ->
                        output.write(MarkdownFormatter.FILE_HEADER.toByteArray(Charsets.UTF_8))
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear file", e)
                false
            }
        }
    }

    override fun exists(): Boolean {
        return try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            docFile?.exists() == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check file existence", e)
            false
        }
    }

    override fun count(): Int {
        val content = readAll()
        return MarkdownFormatter.getTimestampRegex().findAll(content).count()
    }

    override fun initHeader(): Boolean {
        val content = readAll()
        if (content.isBlank()) {
            return try {
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream != null) {
                    outputStream.use { output ->
                        output.write(MarkdownFormatter.FILE_HEADER.toByteArray(Charsets.UTF_8))
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize header", e)
                false
            }
        }
        return true
    }

    override fun deleteEntry(timestamp: String): Boolean {
        synchronized(lock) {
            return try {
                val content = readAll()
                val lines = content.lines().toMutableList()

                // Find the entry with matching timestamp
                // Same logic as FileBasedMarkdownFile for consistency
                var targetStartIndex = -1
                var targetEndIndex = lines.size
                var currentTimestamp = ""

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "deleteEntry called for timestamp: $timestamp")
                    Log.d(TAG, "Total lines in file: ${lines.size}")
                }

                for (i in lines.indices) {
                    val line = lines[i]
                    when {
                        line.matches(Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) -> {
                            // Save previous entry if we found the target
                            if (currentTimestamp == timestamp && targetStartIndex >= 0) {
                                targetEndIndex = i
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Found next entry boundary at line $i, setting end index")
                                }
                                break
                            }

                            currentTimestamp = line.substring(3).trim()
                            if (currentTimestamp == timestamp) {
                                targetStartIndex = i
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "Found target timestamp at line $i: $currentTimestamp")
                                }
                            }
                        }
                    }
                }

                if (targetStartIndex < 0) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG, "Failed to find timestamp: $timestamp")
                    }
                    return false
                }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Deleting from line $targetStartIndex to $targetEndIndex")
                }

                // Remove the entry (including its separator before it)
                val deleteFrom = if (targetStartIndex > 0 && lines[targetStartIndex - 1].matches(Regex("^---$"))) {
                    targetStartIndex - 1
                } else {
                    targetStartIndex
                }

                lines.subList(deleteFrom, targetEndIndex).clear()

                // Write back
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream != null) {
                    outputStream.use { output ->
                        output.write(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Delete successful, remaining lines: ${lines.size}")
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete entry", e)
                false
            }
        }
    }

    fun getFileName(): String {
        return try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            docFile?.name ?: "Appendo.md"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file name", e)
            "Appendo.md"
        }
    }
}
