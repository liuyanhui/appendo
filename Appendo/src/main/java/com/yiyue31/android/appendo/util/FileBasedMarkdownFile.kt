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

    companion object {
        private const val TAG = "FileBasedMarkdownFile"
    }

    override fun append(content: String): Boolean = synchronized(FileOperationLock) {
        try {
            val current = readAll()
            val timestamp = EntryParser.nextTimestamp(current)
            appendEntryInternal(timestamp, content, current)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append content", e)
            false
        }
    }

    /**
     * Append with explicit timestamp（v1.1，覆写接口默认实现以真正保留时间戳）。
     * 用于归档恢复等场景；单调性不在此强制（时间戳由调用方决定）。
     */
    override fun appendEntry(timestamp: String, content: String): Boolean = synchronized(FileOperationLock) {
        try {
            val current = readAll()
            appendEntryInternal(timestamp, content, current)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to appendEntry content", e)
            false
        }
    }

    /** 读当前内容 + 追加格式化条目 + 原子写全量。调用方持锁。 */
    private fun appendEntryInternal(timestamp: String, content: String, current: String): Boolean {
        val entry = EntryParser.format(timestamp, content)
        val full = if (current.isBlank()) MarkdownFormatter.FILE_HEADER + entry else current + entry
        return atomicWrite(full.toByteArray(Charsets.UTF_8))
    }

    override fun readAll(): String = synchronized(FileOperationLock) {
        try {
            FileInputStream(file).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            ""
        }
    }

    override fun clear(): Boolean = synchronized(FileOperationLock) {
        // 不走 .bak（默认模式无 .bak；数据安全由清空前自动归档兜底）。直接原子写 FILE_HEADER。
        atomicWrite(MarkdownFormatter.FILE_HEADER.toByteArray(Charsets.UTF_8))
    }

    override fun exists(): Boolean = synchronized(FileOperationLock) {
        file.exists()
    }

    override fun count(): Int = synchronized(FileOperationLock) {
        // 用 EntryParser 宽松正则（v1.1）：同时数秒级与毫秒级条目，否则新毫秒条目数不到（P1-5）
        val content = readAll()
        EntryParser.getTimestampRegex().findAll(content).count()
    }

    override fun initHeader(): Boolean = synchronized(FileOperationLock) {
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        val content = readAll()
        if (content.isBlank()) {
            atomicWrite(MarkdownFormatter.FILE_HEADER.toByteArray(Charsets.UTF_8))
        } else {
            true
        }
    }

    override fun deleteEntry(timestamp: String): Boolean = synchronized(FileOperationLock) {
        try {
            val lines = readAll().lines()
            val bounds = EntryParser.findEntryBounds(lines, timestamp)
                ?: return@synchronized false
            val newLines = EntryParser.buildDeletedLines(lines, bounds)
            atomicWrite(newLines.joinToString("\n").toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete entry", e)
            false
        }
    }

    override fun updateEntry(timestamp: String, newContent: String): Boolean = synchronized(FileOperationLock) {
        try {
            val lines = readAll().lines()
            val bounds = EntryParser.findEntryBounds(lines, timestamp)
                ?: return@synchronized false
            val newLines = EntryParser.buildUpdatedLines(lines, bounds, newContent)
            atomicWrite(newLines.joinToString("\n").toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update entry", e)
            false
        }
    }

    override fun writeAll(content: String): Boolean = synchronized(FileOperationLock) {
        atomicWrite(content.toByteArray(Charsets.UTF_8))
    }

    /**
     * 原子写入（v1.1）：写同目录临时文件 → fsync → rename → 失败 fallback 覆写。
     * - 同目录 createTempFile 避免 rename 跨文件系统 EXDEV。
     * - 写前清理上次崩溃残留的孤儿 tmp（`file.name + ".tmp"` 前缀）。
     * - 调用方须已持 FileOperationLock（本方法不再加锁，避免重复；JVM 内置锁可重入）。
     */
    private fun atomicWrite(content: ByteArray): Boolean {
        val parent = file.parentFile ?: return false
        if (!parent.exists()) parent.mkdirs()
        parent.listFiles { f -> f.name.startsWith(file.name + ".tmp") }?.forEach { it.delete() }
        val tmp = File.createTempFile(file.name + ".tmp_", ".md", parent)
        return try {
            FileOutputStream(tmp).use { output ->
                output.write(content)
                output.fd.sync()
            }
            if (tmp.renameTo(file)) {
                true
            } else {
                // rename 失败 fallback：直接覆写（非原子，但尽力保数据）
                FileOutputStream(file).use { it.write(content) }
                tmp.delete()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "atomicWrite failed", e)
            tmp.delete()
            false
        }
    }
}
