package com.yiyue31.android.appendo.ui

import com.yiyue31.android.appendo.util.MoveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MoveOutcomeMapping 全组合钉死（戊批次 T-26，设计 §25.1——MoveResult 全态×动词×useSAF，
 * 沿 M1 决策映射先例）：移出/转移组合；移入组合随 T-28 迁入补。纯 JVM。
 */
class MoveOutcomeMappingTest {

    private val source = "源主题"
    private val target = "目标主题"

    // ---------- 成功三态 × 两动词 ----------

    @Test
    fun success_moveOut_movedOnly() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(3, 0, 0), MoveVerb.MOVE_OUT, useSAF = false, sourceName = source, targetName = target)
        assertEquals(Microcopy.toastMovedOutCount(3), o.toast)
        assertFalse(o.keepRetryState)
        assertTrue("移出方向成功→主列表刷新信号", o.refreshMain)
    }

    @Test
    fun success_moveOut_skipped() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(2, 1, 0), MoveVerb.MOVE_OUT, useSAF = true, sourceName = source, targetName = target)
        assertEquals(Microcopy.toastMovedOutSkipped(2, 1), o.toast)
        assertTrue(o.refreshMain)
    }

    @Test
    fun success_moveOut_allExisting() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(0, 5, 0), MoveVerb.MOVE_OUT, useSAF = false, sourceName = source, targetName = target)
        assertEquals("N=0 移出专用句（100）", Microcopy.toastMovedOutAllExisting(5), o.toast)
        assertTrue(o.refreshMain)
    }

    @Test
    fun success_transfer_movedOnly_reusesMoveInFamily() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(3, 0, 0), MoveVerb.TRANSFER, useSAF = false, sourceName = source, targetName = target)
        assertEquals("转移 N>0 复用移入词族（§24.2 动词归属正确）", Microcopy.toastMovedCount(3), o.toast)
        assertFalse("转移不触发主列表刷新", o.refreshMain)
    }

    @Test
    fun success_transfer_skipped() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(2, 1, 0), MoveVerb.TRANSFER, useSAF = false, sourceName = source, targetName = target)
        assertEquals(Microcopy.toastMovedCountSkipped(2, 1), o.toast)
    }

    @Test
    fun success_transfer_allExisting_dedicatedVariant() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(0, 4, 0), MoveVerb.TRANSFER, useSAF = false, sourceName = source, targetName = target)
        assertEquals(
            "转移 N=0 专用变体（评审 E4：禁复用主列表句——源＝主题时事实错误）",
            Microcopy.toastMovedAllExistingTransfer(4, target, source),
            o.toast
        )
    }

    // ---------- 失败族 × 两动词 × useSAF ----------

    @Test
    fun failures_moveOut_plainFamily_keepRetry() {
        for (r in listOf(MoveResult.SourceReadFailed, MoveResult.TargetWriteFailed, MoveResult.SourceDeleteFailed(1, 0, 0))) {
            val o = MoveOutcomeMapping.map(r, MoveVerb.MOVE_OUT, useSAF = false, sourceName = source, targetName = target)
            assertEquals("统一兜底句＋重试保持：$r", Microcopy.TOAST_MOVE_OUT_FAILED, o.toast)
            assertTrue(o.keepRetryState)
            assertFalse(o.refreshMain)
        }
    }

    @Test
    fun targetReadFailed_moveOut_useSAF_safVariant() {
        val o = MoveOutcomeMapping.map(MoveResult.TargetReadFailed, MoveVerb.MOVE_OUT, useSAF = true, sourceName = source, targetName = target)
        assertEquals("移出目标读失败＋SAF 主文件→冒号句式变体（§21.2/A10）", Microcopy.TOAST_MOVE_OUT_FAILED_SAF, o.toast)
        assertTrue(o.keepRetryState)
    }

    @Test
    fun targetReadFailed_moveOut_default_plain() {
        val o = MoveOutcomeMapping.map(MoveResult.TargetReadFailed, MoveVerb.MOVE_OUT, useSAF = false, sourceName = source, targetName = target)
        assertEquals("默认模式目标读失败→统一兜底句", Microcopy.TOAST_MOVE_OUT_FAILED, o.toast)
    }

    @Test
    fun failures_transfer_neverSafVariant() {
        // 转移目标恒 FileBased 主题——useSAF 不产生 SAF 变体（含 true 传入的防御断言）
        for (saf in listOf(false, true)) {
            val o = MoveOutcomeMapping.map(MoveResult.TargetReadFailed, MoveVerb.TRANSFER, useSAF = saf, sourceName = source, targetName = target)
            assertEquals(Microcopy.TOAST_MOVE_OUT_FAILED, o.toast)
        }
    }

    @Test
    fun sourceReadFailed_transfer_plain() {
        val o = MoveOutcomeMapping.map(MoveResult.SourceReadFailed, MoveVerb.TRANSFER, useSAF = true, sourceName = source, targetName = target)
        assertEquals("转移源＝FileBased 主题，与 SAF 无关", Microcopy.TOAST_MOVE_OUT_FAILED, o.toast)
        assertTrue(o.keepRetryState)
    }

    // ---------- MOVE_IN 组合（WA-1：MainScreen 迁移随入） ----------

    @Test
    fun success_moveIn_reusesFamilyAndRefreshesMain() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(3, 0, 0), MoveVerb.MOVE_IN, useSAF = false, sourceName = "主列表", targetName = "T")
        assertEquals(Microcopy.toastMovedCount(3), o.toast)
        assertFalse(o.keepRetryState)
        assertTrue("移入成功＝条目离开主文件→刷新信号", o.refreshMain)
    }

    @Test
    fun success_moveIn_allExisting_dedicatedToast() {
        val o = MoveOutcomeMapping.map(MoveResult.Success(0, 5, 0), MoveVerb.MOVE_IN, useSAF = false, sourceName = "主列表", targetName = "T")
        assertEquals(Microcopy.toastMovedAllExisting(5), o.toast)
    }

    @Test
    fun success_moveIn_guideSuffixAppended_onlyForMoveIn() {
        val withGuide = MoveOutcomeMapping.map(MoveResult.Success(1, 0, 0), MoveVerb.MOVE_IN, useSAF = false, sourceName = "主列表", targetName = "T", guideSuffix = Microcopy.firstMoveGuideSuffix)
        assertEquals(Microcopy.toastMovedCount(1) + Microcopy.firstMoveGuideSuffix, withGuide.toast)
        assertEquals(Microcopy.firstMoveGuideSuffix, withGuide.guideSuffix)
        val notForOut = MoveOutcomeMapping.map(MoveResult.Success(1, 0, 0), MoveVerb.MOVE_OUT, useSAF = false, sourceName = "S", targetName = "T", guideSuffix = Microcopy.firstMoveGuideSuffix)
        assertEquals("指路仅移入动词（§15.4）", Microcopy.toastMovedOutCount(1), notForOut.toast)
    }

    @Test
    fun sourceReadFailed_moveIn_safVariant() {
        val o = MoveOutcomeMapping.map(MoveResult.SourceReadFailed, MoveVerb.MOVE_IN, useSAF = true, sourceName = "主列表", targetName = "T")
        assertEquals("移入源＝主文件，SAF 授权失效变体", Microcopy.TOAST_MOVE_FAILED_SAF_REVOKED, o.toast)
        assertTrue(o.keepRetryState)
    }

    @Test
    fun sourceReadFailed_moveIn_default_plain() {
        val o = MoveOutcomeMapping.map(MoveResult.SourceReadFailed, MoveVerb.MOVE_IN, useSAF = false, sourceName = "主列表", targetName = "T")
        assertEquals(Microcopy.TOAST_MOVE_FAILED, o.toast)
    }

    @Test
    fun targetFailures_moveIn_targetWriteFamily() {
        for (r in listOf(MoveResult.TargetReadFailed, MoveResult.TargetWriteFailed)) {
            val o = MoveOutcomeMapping.map(r, MoveVerb.MOVE_IN, useSAF = false, sourceName = "主列表", targetName = "T")
            assertEquals("移入目标恒 FileBased 主题——沿目标写句式：$r", Microcopy.toastMoveFailedTargetWrite(), o.toast)
            assertTrue(o.keepRetryState)
            assertFalse(o.refreshMain)
        }
    }

    @Test
    fun sourceDeleteFailed_moveIn_retryAndRefresh() {
        val o = MoveOutcomeMapping.map(MoveResult.SourceDeleteFailed(1, 0, 0), MoveVerb.MOVE_IN, useSAF = false, sourceName = "主列表", targetName = "T")
        assertEquals(Microcopy.toastMoveFailedSourceDelete(), o.toast)
        assertTrue("对话框保持重试（84(b)）", o.keepRetryState)
        assertTrue("移入源删失败＝主文件已部分移除→即刻如实刷新（§6.6）", o.refreshMain)
    }

    @Test
    fun sourceDeleteFailed_moveOut_noRefresh() {
        val o = MoveOutcomeMapping.map(MoveResult.SourceDeleteFailed(1, 0, 0), MoveVerb.MOVE_OUT, useSAF = false, sourceName = "S", targetName = "主列表")
        assertEquals(Microcopy.TOAST_MOVE_OUT_FAILED, o.toast)
        assertTrue(o.keepRetryState)
        assertFalse("移出源删失败＝主题侧部分移除，主文件未动不刷新", o.refreshMain)
    }

    @Test
    fun success_refreshMain_byVerb() {
        assertTrue(MoveOutcomeMapping.map(MoveResult.Success(1, 0, 0), MoveVerb.MOVE_IN, false, "s", "t").refreshMain)
        assertTrue(MoveOutcomeMapping.map(MoveResult.Success(1, 0, 0), MoveVerb.MOVE_OUT, false, "s", "t").refreshMain)
        assertFalse("转移不动主文件", MoveOutcomeMapping.map(MoveResult.Success(1, 0, 0), MoveVerb.TRANSFER, false, "s", "t").refreshMain)
    }

    // ---------- SameDocument 防御 ----------

    @Test
    fun sameDocument_guardToast_noStateChange() {
        for (verb in MoveVerb.entries) {
            val o = MoveOutcomeMapping.map(MoveResult.SameDocument, verb, useSAF = false, sourceName = source, targetName = target)
            assertEquals("防御兜底 toast（96；正常不可达）", Microcopy.toastSameTopicGuard(), o.toast)
            assertFalse("不设失败重试态", o.keepRetryState)
            assertFalse(o.refreshMain)
        }
    }
}
