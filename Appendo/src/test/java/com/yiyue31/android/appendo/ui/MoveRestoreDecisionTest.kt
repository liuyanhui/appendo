package com.yiyue31.android.appendo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MoveRestoreDecision] 2×2 全组合（N-4/TD-051 设计 §3.3；真机在途窗口不可靠命中——JVM 钉死，
 * TC-102 主判据）。
 */
class MoveRestoreDecisionTest {

    @Test
    fun notRestoredExecuting_none() {
        assertEquals(MoveRestoreDecision.Action.NONE, MoveRestoreDecision.decide(false, true))
        assertEquals(MoveRestoreDecision.Action.NONE, MoveRestoreDecision.decide(false, false))
    }

    @Test
    fun executing_keysEmpty_closeFlow() {
        // 事务实际已完成（条目已不在源）——静默关流程，不发假失败信号
        assertEquals(MoveRestoreDecision.Action.CLOSE_FLOW, MoveRestoreDecision.decide(true, true))
    }

    @Test
    fun executing_keysRemain_keepRetry() {
        // 未发生/部分完成（84(d) 软中间态）——保持重试，幂等收敛
        assertEquals(MoveRestoreDecision.Action.KEEP_RETRY, MoveRestoreDecision.decide(true, false))
    }
}