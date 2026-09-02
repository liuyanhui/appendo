package com.yiyue31.android.appendo.util

/**
 * 列表刷新守卫（TD-016，纯函数）：决定 `MainScreen.refreshEntryCount` 在一次读取后
 * 是否**跳过**刷新与孤儿对账。
 *
 * 判据（按可疑度）：
 * - **读失败**（`readFailed`）→ 跳过：读不到 ≠ 空文件（TD-012，防止误杀全部提醒）；
 * - **内容空白**（连文件头都没有）且解析为空且上轮非空 → 跳过：疑似异常读取
 *   （读失败也产出空白串，此条是不依赖 failed 标志的兜底）；
 * - **header-only**（内容非空白）且解析为空 → **放行**：这是合法空文件——
 *   删除最后一条条目 / 清空后的正常状态（TD-016 回归：旧守卫"结果为空且上轮非空"
 *   会把它拦下，导致已删条目滞留 UI 直至重启）。
 */
object RefreshGuard {

    fun shouldSkipRefresh(
        readFailed: Boolean,
        content: String,
        newEntryCount: Int,
        prevEntryCount: Int
    ): Boolean {
        if (readFailed) return true
        return content.isBlank() && newEntryCount == 0 && prevEntryCount > 0
    }
}
