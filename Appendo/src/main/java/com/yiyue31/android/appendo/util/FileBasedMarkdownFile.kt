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
}
