package com.example.linkappending.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileBasedMarkdownFile(
    private val context: Context,
    private val file: File
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
                FileOutputStream(file, true).use { output ->
                    output.write(entry.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun readAll(): String {
        return try {
            FileInputStream(file).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun clear(): Boolean {
        synchronized(lock) {
            return try {
                FileOutputStream(file).use { output ->
                    output.write(FILE_HEADER.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun exists(): Boolean {
        return file.exists()
    }

    fun count(): Int {
        val content = readAll()
        return TIMESTAMP_REGEX.findAll(content).count()
    }

    fun initHeader(): Boolean {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val content = readAll()
        if (content.isBlank()) {
            return try {
                FileOutputStream(file).use { output ->
                    output.write(FILE_HEADER.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (_: Exception) {
                false
            }
        }
        return true
    }

    private fun formatEntry(content: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        return "\n---\n\n## $timestamp\n\n$content\n\n---\n"
    }
}
