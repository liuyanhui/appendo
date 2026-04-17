package com.yiyue31.android.appendo.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileBasedMarkdownFile(
    private val context: Context,
    private val file: File
) : MarkdownFileOperations {
    private val lock = Any()

    companion object {
        private const val TAG = "FileBasedMarkdownFile"
    }

    override fun append(content: String): Boolean {
        synchronized(lock) {
            return try {
                val entry = MarkdownFormatter.formatEntry(content)
                FileOutputStream(file, true).use { output ->
                    output.write(entry.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to append content", e)
                false
            }
        }
    }

    override fun readAll(): String {
        return try {
            FileInputStream(file).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            ""
        }
    }

    override fun clear(): Boolean {
        synchronized(lock) {
            return try {
                FileOutputStream(file).use { output ->
                    output.write(MarkdownFormatter.FILE_HEADER.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear file", e)
                false
            }
        }
    }

    override fun exists(): Boolean {
        return file.exists()
    }

    override fun count(): Int {
        val content = readAll()
        return MarkdownFormatter.getTimestampRegex().findAll(content).count()
    }

    override fun initHeader(): Boolean {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val content = readAll()
        if (content.isBlank()) {
            return try {
                FileOutputStream(file).use { output ->
                    output.write(MarkdownFormatter.FILE_HEADER.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize header", e)
                false
            }
        }
        return true
    }

    override fun deleteEntry(index: Int): Boolean {
        synchronized(lock) {
            return try {
                val content = readAll()
                val lines = content.lines().toMutableList()

                // Find all entry boundaries (lines with ## timestamp)
                val entryBoundaries = mutableListOf<Int>()
                lines.forEachIndexed { i, line ->
                    if (line.matches(Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$"))) {
                        entryBoundaries.add(i)
                    }
                }

                if (index < 0 || index >= entryBoundaries.size) {
                    return false
                }

                // Determine the range to delete
                val startIndex = entryBoundaries[index]
                val endIndex = if (index + 1 < entryBoundaries.size) {
                    entryBoundaries[index + 1]
                } else {
                    lines.size
                }

                // Remove the entry (including its separator before it)
                val deleteFrom = if (startIndex > 0 && lines[startIndex - 1].matches(Regex("^---$"))) {
                    startIndex - 1
                } else {
                    startIndex
                }

                lines.subList(deleteFrom, endIndex).clear()

                // Write back
                FileOutputStream(file).use { output ->
                    output.write(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete entry", e)
                false
            }
        }
    }
}
