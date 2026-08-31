package com.yiyue31.android.appendo.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

/**
 * SAF 存储实现（v1.1 全面升级）。
 *
 * - 读写经 EntryParser（毫秒时间戳、ZWSP 隔离、findEntryBounds 等），与 FileBasedMarkdownFile 同源。
 * - 写入用 .pending + .bak 软恢复（存应用私有目录）：写前备份+标记，写后删标记；崩溃残留 .pending →
 *   下次读 ensureConsistent 从 .bak 回滚（最近一次未确认写入可能丢失，但不损坏文件，specs 39/46）。
 * - 所有读写路径 synchronized(FileOperationLock)。
 *
 * 注：SAF 路径需真机/模拟器验证（instrumented），本环境无法跑。
 */
class SafMarkdownFile(
    private val context: Context,
    private val uri: Uri
) : MarkdownFileOperations {

    companion object {
        private const val TAG = "SafMarkdownFile"
    }

    private val contentResolver = context.contentResolver

    // ---- SAF 软恢复：.pending 标记 + .bak 备份（应用私有目录，文件名按 URI 哈希派生）----
    private val bakFile: File get() = recoveryFile("bak")
    private val pendingFile: File get() = recoveryFile("pending")
    private fun recoveryFile(suffix: String): File =
        File(context.filesDir, "appendo_saf_${uri.hashCode()}_$suffix")

    // ==================== 读 ====================

    override fun readAll(): String = readAllWithStatus().content

    /** 读前 ensureConsistent（.pending 存在则从 .bak 回滚），返回内容 + recovered/failed 标志（覆写接口默认）。 */
    override fun readAllWithStatus(): ReadResult = synchronized(FileOperationLock) {
        val recovered = ensureConsistent()
        val (content, readFailed) = readMainForStatus()
        ReadResult(content, recovered, readFailed)
    }

    private fun readMainOnly(): String = readMainForStatus().first

    /**
     * 写路径专用读（TD-013）：先恢复（.pending 残留 → 从 .bak 回滚）再读主文件，
     * 确保 .bak 始终基于上次确认良好的状态、崩溃后的脏内容不会经写路径固化。调用方持锁。
     */
    private fun readMainForWrite(): Pair<String, Boolean> {
        ensureConsistent()
        return readMainForStatus()
    }

    /**
     * 读主文件并区分失败（TD-012）：Pair(内容, 是否读失败)。调用方持锁。
     * openInputStream 返回 null（无法打开流）同样视为读失败——不得当空文件处理。
     */
    private fun readMainForStatus(): Pair<String, Boolean> {
        return try {
            val content = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }
            if (content != null) {
                content to false
            } else {
                Log.w(TAG, "openInputStream returned null")
                "" to true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            "" to true
        }
    }

    override fun exists(): Boolean = synchronized(FileOperationLock) {
        try {
            DocumentFile.fromSingleUri(context, uri)?.exists() == true
        } catch (e: SecurityException) {
            // 授权失效（上层 isFileUriValid 会切默认模式）；区别于真不存在，单独记录
            Log.w(TAG, "exists: SecurityException (auth revoked?)", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "exists check failed", e)
            false
        }
    }

    override fun count(): Int = synchronized(FileOperationLock) {
        EntryParser.getTimestampRegex().findAll(readAll()).count()
    }

    // ==================== 恢复 ====================

    /**
     * SAF 软恢复判定（调用方持锁）：.pending 存在表示上次写未完成、主可能脏。
     * 有 .bak → 用 .bak 覆写主 + 删 .pending + 返回 true（recovered）；无 .bak → 删 .pending + false。
     */
    private fun ensureConsistent(): Boolean {
        if (!pendingFile.exists()) return false
        val action = EntryParser.decideRecovery(pendingExists = true, bakExists = bakFile.exists())
        return if (action.useBackup && bakFile.exists()) {
            try {
                writeMain(bakFile.readText())
                pendingFile.delete()
                true
            } catch (e: Exception) {
                Log.e(TAG, "ensureConsistent recovery failed", e)
                pendingFile.delete() // 避免反复尝试
                false
            }
        } else {
            pendingFile.delete()
            false
        }
    }

    // ==================== 写 ====================

    override fun append(content: String): Boolean = synchronized(FileOperationLock) {
        try {
            val (current, readFailed) = readMainForWrite()
            if (readFailed) return@synchronized false // 读失败中止：禁止全量重写（TD-012）
            val timestamp = EntryParser.nextTimestamp(current)
            appendEntryInternal(timestamp, content, current)
        } catch (e: Exception) {
            Log.e(TAG, "append failed", e)
            false
        }
    }

    /** 覆写接口默认实现，真正保留传入时间戳（归档恢复用）。 */
    override fun appendEntry(timestamp: String, content: String): Boolean = synchronized(FileOperationLock) {
        try {
            val (current, readFailed) = readMainForWrite()
            if (readFailed) return@synchronized false // 读失败中止（TD-012）
            appendEntryInternal(timestamp, content, current)
        } catch (e: Exception) {
            Log.e(TAG, "appendEntry failed", e)
            false
        }
    }

    private fun appendEntryInternal(timestamp: String, content: String, current: String): Boolean {
        val entry = EntryParser.format(timestamp, content)
        val full = if (current.isBlank()) MarkdownFormatter.FILE_HEADER + entry else current + entry
        return safAtomicWrite(full, current)
    }

    override fun deleteEntry(timestamp: String): Boolean = synchronized(FileOperationLock) {
        try {
            val (current, readFailed) = readMainForWrite()
            if (readFailed) return@synchronized false // 读失败中止（TD-012）
            val lines = current.lines()
            val bounds = EntryParser.findEntryBounds(lines, timestamp)
                ?: return@synchronized false
            val newLines = EntryParser.buildDeletedLines(lines, bounds)
            safAtomicWrite(newLines.joinToString("\n"), current)
        } catch (e: Exception) {
            Log.e(TAG, "deleteEntry failed", e)
            false
        }
    }

    override fun updateEntry(timestamp: String, newContent: String): Boolean = synchronized(FileOperationLock) {
        try {
            val (current, readFailed) = readMainForWrite()
            if (readFailed) return@synchronized false // 读失败中止（TD-012）
            val lines = current.lines()
            val bounds = EntryParser.findEntryBounds(lines, timestamp)
                ?: return@synchronized false
            val newLines = EntryParser.buildUpdatedLines(lines, bounds, newContent)
            safAtomicWrite(newLines.joinToString("\n"), current)
        } catch (e: Exception) {
            Log.e(TAG, "updateEntry failed", e)
            false
        }
    }

    /** clear 不走 .bak（数据安全由清空前自动归档兜底，specs 13）；直接写 FILE_HEADER。 */
    override fun clear(): Boolean = synchronized(FileOperationLock) {
        writeMain(MarkdownFormatter.FILE_HEADER)
    }

    override fun initHeader(): Boolean = synchronized(FileOperationLock) {
        val (content, readFailed) = readMainForWrite()
        if (readFailed) return@synchronized false // 读失败不得当空文件覆写（TD-012）
        if (content.isBlank()) writeMain(MarkdownFormatter.FILE_HEADER) else true
    }

    override fun writeAll(content: String): Boolean = synchronized(FileOperationLock) {
        val (current, readFailed) = readMainForWrite()
        if (readFailed) return@synchronized false // 读失败不写、不污染 .bak（TD-012）
        safAtomicWrite(content, current)
    }

    /**
     * SAF 原子写（软恢复语义，调用方持锁）：
     * 1. 备份当前主内容到 .bak + 建 .pending 标记
     * 2. 写主 URI（尽力 fsync）
     * 3. 成功删 .pending（崩溃则 .pending 残留 → 下次读 ensureConsistent 从 .bak 回滚）
     */
    private fun safAtomicWrite(newContent: String, currentForBackup: String): Boolean {
        return try {
            bakFile.writeText(currentForBackup)
            pendingFile.writeText("")
            val written = writeMain(newContent)
            if (written) pendingFile.delete()
            written
        } catch (e: Exception) {
            Log.e(TAG, "safAtomicWrite failed", e)
            false // .pending 残留 → 下次读恢复
        }
    }

    /**
     * 写主 URI。优先 openFileDescriptor（可 fsync）；不支持/返回 null/抛异常则回退 openOutputStream。
     * fsync 尽力而为、不依赖（.bak 兜底损坏，specs 39 软恢复）。
     */
    private fun writeMain(content: String): Boolean {
        return try {
            contentResolver.openFileDescriptor(uri, "wt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    try { pfd.fileDescriptor.sync() } catch (_: Exception) { /* best-effort */ }
                }
                true
            } ?: writeMainViaOutputStream(content)
        } catch (e: Exception) {
            // 某些 provider 不支持 openFileDescriptor → 回退
            writeMainViaOutputStream(content)
        }
    }

    private fun writeMainViaOutputStream(content: String): Boolean {
        return try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
        } catch (e: Exception) {
            Log.e(TAG, "writeMainViaOutputStream failed", e)
            false
        }
    }
}
