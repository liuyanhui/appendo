package com.yiyue31.android.appendo.data

import android.content.Context
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.MarkdownFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing an archive file
 */
data class ArchiveFile(
    val file: File,
    val name: String,
    val timestamp: Date,
    val entryCount: Int
)

/**
 * Repository for managing archive files
 */
class ArchiveRepository(private val context: Context) {

    companion object {
        private const val ARCHIVE_PREFIX = "Appendo_"
        private const val ARCHIVE_SUFFIX = ".md"
        private val DATE_FORMAT = SimpleDateFormat(MarkdownFormatter.ARCHIVE_TIMESTAMP_FORMAT, Locale.getDefault())
    }

    /**
     * Get the directory where archive files are stored
     */
    private fun getArchiveDirectory(): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    /**
     * List all archive files sorted by date (newest first)
     */
    fun listArchiveFiles(): List<ArchiveFile> {
        val dir = getArchiveDirectory()
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(ARCHIVE_PREFIX) && it.name.endsWith(ARCHIVE_SUFFIX) }
            ?.mapNotNull { file ->
                parseArchiveFile(file)
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    /**
     * Parse archive file metadata
     */
    private fun parseArchiveFile(file: File): ArchiveFile? {
        val timestamp = parseTimestampFromFileName(file.name) ?: return null
        val entryCount = countEntries(file)

        return ArchiveFile(
            file = file,
            name = file.name,
            timestamp = timestamp,
            entryCount = entryCount
        )
    }

    /**
     * Parse timestamp from filename (e.g., "Appendo_20260417_143022.md")
     */
    private fun parseTimestampFromFileName(filename: String): Date? {
        return try {
            val timestampStr = filename
                .removePrefix(ARCHIVE_PREFIX)
                .removeSuffix(ARCHIVE_SUFFIX)
            DATE_FORMAT.parse(timestampStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Count entries in archive file
     */
    private fun countEntries(file: File): Int {
        return try {
            // 宽松正则（TD-010）：秒级/毫秒级条目都数得到，与主列表计数同源
            EntryParser.getTimestampRegex().findAll(file.readText()).count()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get archive file content
     */
    fun getArchiveContent(file: File): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Delete archive file
     */
    fun deleteArchive(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Format timestamp for display
     */
    fun formatTimestamp(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
    }
}
