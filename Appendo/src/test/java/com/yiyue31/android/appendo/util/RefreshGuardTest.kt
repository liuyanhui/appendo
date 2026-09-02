package com.yiyue31.android.appendo.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RefreshGuard 单测（TD-016）：核心是「header-only 合法空文件必须放行刷新」——
 * 旧内联守卫（结果为空且上轮非空即跳过）在此场景误拦，导致删除最后一条后 UI 滞留。
 */
class RefreshGuardTest {

    @Test
    fun readFailure_skips() { // TD-012 保护不回归
        assertTrue(RefreshGuard.shouldSkipRefresh(readFailed = true, content = "", newEntryCount = 0, prevEntryCount = 5))
    }

    @Test
    fun blankContent_withPrevEntries_skips() { // 空白内容兜底（不依赖 failed 标志）
        assertTrue(RefreshGuard.shouldSkipRefresh(readFailed = false, content = "", newEntryCount = 0, prevEntryCount = 5))
        assertTrue(RefreshGuard.shouldSkipRefresh(readFailed = false, content = "  \n ", newEntryCount = 0, prevEntryCount = 1))
    }

    @Test
    fun headerOnly_afterDeleteLast_passesRefresh() { // TD-016 复现用例：合法空文件放行
        assertFalse(
            "删除最后一条后的 header-only 是合法空文件，必须刷新（否则 UI 滞留已删条目）",
            RefreshGuard.shouldSkipRefresh(readFailed = false, content = "# Appendo\n", newEntryCount = 0, prevEntryCount = 5)
        )
    }

    @Test
    fun blankContent_firstLaunch_passesRefresh() { // 首启无上轮数据：无提醒可误杀，放行
        assertFalse(RefreshGuard.shouldSkipRefresh(readFailed = false, content = "", newEntryCount = 0, prevEntryCount = 0))
    }

    @Test
    fun normalNonEmptyResult_passesRefresh() {
        assertFalse(
            RefreshGuard.shouldSkipRefresh(
                readFailed = false,
                content = "# Appendo\n\n---\n\n## 2026-08-31 09:00:00.000\n\nx\n",
                newEntryCount = 1,
                prevEntryCount = 1
            )
        )
    }
}
