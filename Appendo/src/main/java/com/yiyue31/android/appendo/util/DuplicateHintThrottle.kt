package com.yiyue31.android.appendo.util

/**
 * 重复内容提示节流（v1.1，specs 42）：同内容在 [windowMs] 内只提示一次，
 * 防止自动化分享/快捷指令刷屏。进程内单例（跨 Activity 持久）。
 *
 * 测试：通过 [clock] 注入伪时间 + [resetForTest] 清状态。
 */
object DuplicateHintThrottle {

    /** 时间源，测试可覆写。 */
    var clock: () -> Long = { System.currentTimeMillis() }

    private const val windowMs = 5000L
    private val lastShown = mutableMapOf<String, Long>()

    /**
     * 是否应展示提示。首次或距上次 >= [windowMs] 返回 true 并记录时间；窗口内返回 false（不更新）。
     */
    fun shouldShow(content: String): Boolean {
        val now = clock()
        val last = lastShown[content]
        return if (last == null || now - last >= windowMs) {
            lastShown[content] = now
            true
        } else {
            false
        }
    }

    /** 测试用：清空状态。 */
    fun resetForTest() {
        lastShown.clear()
    }
}
