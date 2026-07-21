package com.yiyue31.android.appendo.util

/**
 * [MarkdownFileOperations.readAllWithStatus] 的返回：文件内容 + 恢复标志。
 *
 * - [content]：文件全文（含 ZWSP 隔离标记，供 EntryParser.parse 内部解析时剥离还原）。
 * - [recovered]：SAF 软恢复是否发生（上次写入未完成，已从 .bak 回滚到上次良好状态）。
 *   默认文件模式恒为 false。UI 据此提示用户（specs 46 透明提示）。
 */
data class ReadResult(
    val content: String,
    val recovered: Boolean
)
