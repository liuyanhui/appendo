package com.yiyue31.android.appendo.util

/**
 * 文件级常量集中处。条目的解析/格式化/算法知识全部在 [EntryParser]（TD-011 清理后本对象仅承载常量：
 * v1.2.1 移除了无生产调用方的 formatEntry / TIMESTAMP_FORMAT / 严格正则）。
 */
object MarkdownFormatter {
    const val FILE_HEADER = "# Appendo\n"
    const val ARCHIVE_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

    /** 分隔线形态：parse 跳过 + 写侧 ZWSP 隔离共用（TD-015，勿与 EntryParser 判定分叉）。 */
    const val SEPARATOR_LINE = "---"

    /** 文件头前缀形态（# Appendo）：parse 跳过 + 写侧 ZWSP 隔离共用（TD-015）。 */
    val FILE_HEADER_PREFIX: String = FILE_HEADER.trim()
}
