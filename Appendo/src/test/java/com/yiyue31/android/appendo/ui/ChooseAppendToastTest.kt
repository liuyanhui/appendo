package com.yiyue31.android.appendo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** code-review 收敛：chooseAppendToast 基句参数化＋重复旁注经 Microcopy（防硬编码漂移）。 */
class ChooseAppendToastTest {

    @Test
    fun noDuplicate_returnsBase() {
        assertEquals("已追加", chooseAppendToast("x", 0))
        assertEquals("已追加到「T」", chooseAppendToast("x", 0, "已追加到「T」"))
    }

    @Test
    fun duplicate_basePlusSuffix() {
        // 节流窗口放行时旁注＝基句＋Microcopy.duplicateSuffix（主/题共用）
        val main = chooseAppendToast("dupA", 2)
        val topic = chooseAppendToast("dupB", 2, "已追加到「T」")
        assertEquals("已追加" + Microcopy.duplicateSuffix(2), main)
        assertEquals("已追加到「T」" + Microcopy.duplicateSuffix(2), topic)
    }
}
