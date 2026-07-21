package com.yiyue31.android.appendo.util

/**
 * Interface defining common operations for Markdown file implementations.
 * Eliminates code duplication and enables factory pattern usage.
 */
/**
 * Global lock for file operations to prevent concurrent access across instances.
 * MainScreen and ShareReceiverActivity create separate MarkdownFileOperations instances,
 * so instance-level locks don't protect against cross-instance concurrent writes.
 */
object FileOperationLock

interface MarkdownFileOperations {
    /**
     * Append content to the file with timestamp formatting.
     * @return true if successful, false otherwise
     */
    fun append(content: String): Boolean

    /**
     * Read all content from the file.
     * @return Full file content as string, empty string if error
     */
    fun readAll(): String

    /**
     * Clear all content from the file (resets to header only).
     * @return true if successful, false otherwise
     */
    fun clear(): Boolean

    /**
     * Check if the file exists.
     * @return true if file exists, false otherwise
     */
    fun exists(): Boolean

    /**
     * Count the number of entries in the file.
     * @return Number of timestamp-based entries found
     */
    fun count(): Int

    /**
     * Initialize the file with header if it's empty or doesn't exist.
     * @return true if successful, false otherwise
     */
    fun initHeader(): Boolean

    /**
     * Delete a specific entry by its timestamp.
     *
     * NOTE: We use timestamp instead of index because:
     * - Index changes after insert/delete operations, causing misalignment
     * - Timestamp is the unique identifier of each entry, always accurate
     * - Supports consecutive deletions and batch operations
     *
     * @param timestamp The timestamp of the entry to delete (format: "YYYY-MM-DD HH:mm:ss")
     * @return true if successful, false otherwise
     */
    fun deleteEntry(timestamp: String): Boolean

    /**
     * Update the content of an existing entry identified by its timestamp.
     * The timestamp remains unchanged; only the content is replaced.
     *
     * Thread-safe: uses FileOperationLock for atomicity.
     *
     * @param timestamp The timestamp identifying the entry (format: "YYYY-MM-DD HH:mm:ss")
     * @param newContent The new content to replace the old content
     * @return true if successful, false if entry not found or write failed
     */
    fun updateEntry(timestamp: String, newContent: String): Boolean

    /**
     * Write all content to the file, replacing existing content.
     * Unlike append(), this does NOT add a timestamp or format the content.
     * Used for operations that need to write raw markdown content directly.
     *
     * @param content The raw markdown content to write
     * @return true if successful, false otherwise
     */
    fun writeAll(content: String): Boolean

    /**
     * Append content with an EXPLICIT timestamp（v1.1）。
     * 用于归档恢复等需保留原时间戳的场景（使 `timestamp|content` 去重生效）。
     *
     * 单调唯一性不在本方法强制（时间戳由调用方决定）；只有 [append]（now 路径）
     * 通过 EntryParser.nextTimestamp 保证文件内严格单调递增。
     *
     * 默认实现委托给 [append]（忽略时间戳，临时占位，由 FileBased/Saf 在 T-008/T-011 覆写为真正保留时间戳）。
     * @return true if successful, false otherwise
     */
    fun appendEntry(timestamp: String, content: String): Boolean = append(content)

    /**
     * 读取全部内容并剥离 ZWSP 隔离标记（v1.1）——供"离开应用"的出口（复制/分享）使用。
     * 内部解析请用 [readAll]（保留标记，由 EntryParser.parse 在解析时剥离还原）。
     *
     * 默认实现 = [readAll] + EntryParser.stripIsolationMarkers。
     */
    fun readAllForExternal(): String = EntryParser.stripIsolationMarkers(readAll())

    /**
     * 读取全部内容并附带恢复标志（v1.1）。SAF 软恢复发生时 recovered=true（已从 .bak 回滚）；
     * 默认文件模式恒为 false。UI 据此提示用户（specs 46）。
     *
     * 默认实现 = ReadResult([readAll], false)；SAF 在 T-010 覆写。
     */
    fun readAllWithStatus(): ReadResult = ReadResult(readAll(), recovered = false)
}
