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
    val entryCount: Int,
    /** WB-1：来源主题名（新式 `Appendo_<主题名>_<ts>.md`）；旧式/主界面归档＝null。 */
    val sourceTopic: String? = null
)

/**
 * Repository for managing archive files
 */
class ArchiveRepository(private val context: Context) {

    companion object {
        private const val ARCHIVE_PREFIX = "Appendo_"
        private const val ARCHIVE_SUFFIX = ".md"
        private val DATE_FORMAT = SimpleDateFormat(MarkdownFormatter.ARCHIVE_TIMESTAMP_FORMAT, Locale.getDefault())
        /** WB-1 旧式：Appendo_<yyyyMMdd_HHmmss>[_n].md */
        private val OLD_NAME_REGEX = Regex("^Appendo_(\\d{8}_\\d{6})(?:_\\d+)?\\.md$")
        /** WB-1 新式：Appendo_<主题名>_<yyyyMMdd_HHmmss>[_n].md（主题名非贪婪前置、时间戳尾部锚定） */
        private val NEW_NAME_REGEX = Regex("^Appendo_(.+)_(\\d{8}_\\d{6})(?:_\\d+)?\\.md$")
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
    private fun parseArchiveFile(file: File): ArchiveFile? = archiveFileFor(file)

    /**
     * WB-1：单文件元数据解析公开入口（时间戳解析收敛单点——MainActivity 路由消费，
     * 替代其私有 parseTimestampFromFileName；新式名带 sourceTopic）。
     */
    fun archiveFileFor(file: File): ArchiveFile? {
        val parsed = parseArchiveName(file.name) ?: return null
        return ArchiveFile(
            file = file,
            name = file.name,
            timestamp = parsed.first,
            entryCount = countEntries(file),
            sourceTopic = parsed.second
        )
    }

    /**
     * WB-1：尾部锚定解析三类形态（主题名可含下划线/纯数字，只能从尾部匹配时间戳，
     * 不能首段判式）；同秒后缀 `_n` 两式皆含（缺此后缀件列表不可见、恢复失联）。
     * 旧式先试：`Appendo_<ts>[_n].md`→sourceTopic=null；
     * 新式：`Appendo_<主题名>_<ts>[_n].md`→sourceTopic=主题名。
     * 返回 (timestamp, sourceTopic)；不匹配返 null（mapNotNull 剔除）。
     */
    private fun parseArchiveName(filename: String): Pair<Date, String?>? {
        val oldMatch = OLD_NAME_REGEX.matchEntire(filename)
        if (oldMatch != null) {
            val ts = parseTs(oldMatch.groupValues[1]) ?: return null
            return ts to null
        }
        val newMatch = NEW_NAME_REGEX.matchEntire(filename)
        if (newMatch != null) {
            val ts = parseTs(newMatch.groupValues[2]) ?: return null
            return ts to newMatch.groupValues[1]
        }
        return null
    }

    private fun parseTs(ts: String): Date? = try {
        DATE_FORMAT.parse(ts)
    } catch (e: Exception) {
        null
    }

    /**
     * Count entries in archive file
     */
    private fun countEntries(file: File): Int {
        // 行为等价消重（T-01）：计数收敛到 EntryParser.countEntries（宽松正则，TD-010）；本包装保留读失败返回 0
        return try {
            EntryParser.countEntries(file.readText())
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
