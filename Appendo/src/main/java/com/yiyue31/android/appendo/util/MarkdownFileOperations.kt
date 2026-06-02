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
}
