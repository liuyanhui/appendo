package com.yiyue31.android.appendo.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yiyue31.android.appendo.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileBasedMarkdownFile(
    private val context: Context,
    private val file: File
) : MarkdownFileOperations {

    companion object {
        private const val TAG = "FileBasedMarkdownFile"
    }

    override fun append(content: String): Boolean {
        synchronized(FileOperationLock) {
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
        synchronized(FileOperationLock) {
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

    override fun deleteEntry(timestamp: String): Boolean {
        synchronized(FileOperationLock) {
            return try {
                val content = readAll()
                val lines = content.lines().toMutableList()

                // Find the entry with matching timestamp
                // We iterate through the file to find the exact timestamp line
                // This approach is O(n) but reliable and accurate
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
                FileOutputStream(file).use { output ->
                    output.write(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
                }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Delete successful, remaining lines: ${lines.size}")
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete entry", e)
                false
            }
        }
    }

    override fun updateEntry(timestamp: String, newContent: String): Boolean {
        synchronized(FileOperationLock) {
            return try {
                val content = readAll()
                val lines = content.lines().toMutableList()

                // Find the entry with matching timestamp
                // Same boundary logic as deleteEntry: use next "## YYYY-MM-DD HH:mm:ss" line as boundary
                var targetStartIndex = -1
                var targetEndIndex = lines.size
                var currentTimestamp = ""

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "updateEntry called for timestamp: $timestamp")
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
                    Log.d(TAG, "Updating entry from line $targetStartIndex to $targetEndIndex")
                }

                // Build new content: keep timestamp line, replace content between timestamp and next entry
                val newLines = mutableListOf<String>()
                // Add all lines before the target entry
                newLines.addAll(lines.subList(0, targetStartIndex))
                // Add the timestamp line (unchanged)
                newLines.add(lines[targetStartIndex])
                // Add the new content
                newLines.add("")
                newLines.add(newContent)
                // If there's a next entry, add blank line separator and the rest
                if (targetEndIndex < lines.size) {
                    newLines.add("")
                    newLines.addAll(lines.subList(targetEndIndex, lines.size))
                }

                // Write back
                FileOutputStream(file).use { output ->
                    output.write(newLines.joinToString("\n").toByteArray(Charsets.UTF_8))
                }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Update successful")
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update entry", e)
                false
            }
        }
    }

    override fun writeAll(content: String): Boolean {
        synchronized(FileOperationLock) {
            return try {
                FileOutputStream(file).use { output ->
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write all content", e)
                false
            }
        }
    }
}
