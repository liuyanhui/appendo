package com.yiyue31.android.appendo.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarkdownFile(
    private val context: Context,
    private val uri: Uri
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val lock = Any()

    companion object {
        const val FILE_HEADER = "# Link Collection\n"
        internal const val TIMESTAMP_PATTERN = "^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"
        internal val TIMESTAMP_REGEX = Regex(TIMESTAMP_PATTERN, RegexOption.MULTILINE)
    }

    fun append(content: String): Boolean {
        synchronized(lock) {
            return try {
                appendInternal(content)
            } catch (_: Exception) {
                try {
                    fallbackAppend(content)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    fun readAll(): String {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                InputStreamReader(input, Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun clear(): Boolean {
        synchronized(lock) {
            return try {
                contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write(FILE_HEADER)
                    }
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun exists(): Boolean {
        return try {
            val stream = contentResolver.openInputStream(uri)
            if (stream == null) return false
            stream.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun count(): Int {
        val content = readAll()
        return TIMESTAMP_REGEX.findAll(content).count()
    }

    private fun appendInternal(content: String): Boolean {
        val entry = formatEntry(content)
        contentResolver.openOutputStream(uri, "wa")?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write(entry)
            }
        }
        return true
    }

    private fun fallbackAppend(content: String): Boolean {
        val existing = readAll()
        val entry = formatEntry(content)
        contentResolver.openOutputStream(uri, "rwt")?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.write(existing)
                writer.write(entry)
            }
        }
        return true
    }

    private fun formatEntry(content: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        return "\n---\n\n## $timestamp\n\n$content\n\n---\n"
    }

    fun initHeader(): Boolean {
        val content = readAll()
        if (content.isBlank()) {
            return try {
                contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write(FILE_HEADER)
                    }
                }
                true
            } catch (_: Exception) {
                false
            }
        }
        return true
    }
}
