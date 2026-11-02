package com.yiyue31.android.appendo.util

/**
 * 条目唯一标识（需求 84(e)/87）：timestamp|content 组合键。
 * content 语义 = EntryParser.parse 产物（trim + ZWSP 还原后），与归档恢复去重（ArchiveListScreen）同源，
 * 同秒碰撞的秒级历史条目由此可区分。
 * 注：不提供 asString() 字符串形态——UI 与事务一律类型化传递；字符串拼接 key 仅存在于既有归档恢复
 * 代码（ArchiveListScreen.kt:316-333），新代码不扩散该形态。
 */
data class EntryKey(val timestamp: String, val content: String) {
    companion object {
        fun of(entry: ParsedEntry): EntryKey = EntryKey(entry.rawTimestamp, entry.content)
    }
}
