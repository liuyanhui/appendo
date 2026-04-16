package com.example.linkappending.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafMarkdownFile(
    private val context: Context,
    private val uri: Uri
) {
    private val lock = Any()

    companion object {
        const val FILE_HEADER = "# Link Collection\n"
        internal const val TIMESTAMP_PATTERN = "^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"
        internal val TIMESTAMP_REGEX = Regex(TIMESTAMP_PATTERN, RegexOption.MULTILINE)
    }

    fun append(content: String): Boolean {
        synchronized(lock) {
            return try {
                val entry = formatEntry(content)
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
                // Fallback on any error
                appendFallback(content)
            }
        }
    }

    private fun appendFallback(content: String): Boolean {
        return try {
            val existingContent = readAll()
            val entry = formatEntry(content)
            val newContent = if (existingContent.isBlank()) {
                FILE_HEADER + entry
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
            false
        }
    }

    fun readAll(): String {
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
        } catch (_: Exception) {
            ""
        }
    }

    fun clear(): Boolean {
        synchronized(lock) {
            return try {
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream != null) {
                    outputStream.use { output ->
                        output.write(FILE_HEADER.toByteArray(Charsets.UTF_8))
                    }
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun exists(): Boolean {
        return try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            docFile?.exists() == true
        } catch (_: Exception) {
            false
        }
    }

    fun count(): Int {
        val content = readAll()
        return TIMESTAMP_REGEX.findAll(content).count()
    }

    fun initHeader(): Boolean {
        val content = readAll()
        if (content.isBlank()) {
            return try {
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream != null) {
                    outputStream.use { output ->
                        output.write(FILE_HEADER.toByteArray(Charsets.UTF_8))
                    }
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
        return true
    }

    fun getFileName(): String {
        return try {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            docFile?.name ?: "Appendo.md"
        } catch (_: Exception) {
            "Appendo.md"
        }
    }

    private fun formatEntry(content: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        return "\n---\n\n## $timestamp\n\n$content\n\n---\n"
    }
}
