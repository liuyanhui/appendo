package com.yiyue31.android.appendo.util

/**
 * Interface defining common operations for Markdown file implementations.
 * Eliminates code duplication and enables factory pattern usage.
 */
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
}
