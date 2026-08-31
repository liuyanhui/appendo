package com.yiyue31.android.appendo.util

/**
 * [MarkdownFileOperations.readAllWithStatus] 的返回：文件内容 + 恢复/失败标志。
 *
 * - [content]：文件全文（含 ZWSP 隔离标记，供 EntryParser.parse 内部解析时剥离还原）。
 * - [recovered]：SAF 软恢复是否发生（上次写入未完成，已从 .bak 回滚到上次良好状态）。
 *   默认文件模式恒为 false。UI 据此提示用户（specs 46 透明提示）。
 * - [failed]：读操作本身是否失败（IO 异常 / 流不可打开）。**失败时 content 为空串，但语义是
 *   "读不到"而非"空文件"**——上层不得据此走覆写 / 清空 / 对账路径（TD-012，architecture.md §4.4）。
 */
data class ReadResult(
    val content: String,
    val recovered: Boolean,
    val failed: Boolean = false
)
