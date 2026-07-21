package com.yiyue31.android.appendo.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared utility for Markdown file formatting and constants.
 * Eliminates code duplication across different file implementations.
 */
object MarkdownFormatter {
    const val FILE_HEADER = "# Appendo\n"
    const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss"
    const val ARCHIVE_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

    private val TIMESTAMP_FORMATTER = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.getDefault())

    /**
     * Formats content as a Markdown entry with timestamp.
     * NOTE: 共享 SimpleDateFormat 非线程安全；调用方须持 FileOperationLock（SafMarkdownFile.append 已持锁）。
     * v1.1：新代码请用 EntryParser.format（java.time、线程安全、ZWSP 隔离）；本方法仅 SafMarkdownFile 过渡期使用，T-011 后移除。
     */
    fun formatEntry(content: String): String {
        val timestamp = TIMESTAMP_FORMATTER.format(Date())
        return "\n---\n\n## $timestamp\n\n$content\n\n---\n"
    }

    /**
     * Regex pattern for matching timestamp lines in Markdown entries.
     */
    fun getTimestampRegex(): Regex {
        return Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", RegexOption.MULTILINE)
    }
}
