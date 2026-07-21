package com.yiyue31.android.appendo.util

/**
 * 纯数据：一条解析后的条目，由 [EntryParser.parse] 产出。
 *
 * - [rawTimestamp]：完整时间戳字符串（新条目毫秒级 `yyyy-MM-dd HH:mm:ss.SSS`，
 *   旧条目秒级 `yyyy-MM-dd HH:mm:ss`），作为条目的唯一 key（删除/更新/去重均用它）。
 * - [content]：还原后的用户原文（内容隔离标记 ZWSP 已剥离）。
 *
 * 属于 util 层纯数据，不依赖 UI；UI 层 [com.yiyue31.android.appendo.ui.LinkEntry]
 * 由本类映射而来（见 T-005）。
 */
data class ParsedEntry(
    val rawTimestamp: String,
    val content: String
)
