package com.yiyue31.android.appendo.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
