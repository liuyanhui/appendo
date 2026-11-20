package com.yiyue31.android.appendo.ui

import com.yiyue31.android.appendo.data.TopicDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [MoveFlowHolder] 快照/恢复语义（N-4/TD-051 设计 §3.3——TC-102 JVM 主判据）。
 */
class MoveFlowHolderRestoreTest {

    private fun topic(name: String = "验收主题", count: Int = 5) = TopicDocument(
        file = File("files/topics/$name.md"),
        displayName = name,
        createdAt = 1795057663684L,
        entryCount = count
    )

    @Test
    fun roundTrip_fullFlowState_preserved() {
        val h = MoveFlowHolder()
        h.start(MoveVerb.TRANSFER, MoveFlowStep.SELECT_TARGET)
        h.selectTarget(topic("目标主题", 7))
        h.newTopicInput = "命名草稿"
        h.markFailed()

        val r = MoveFlowHolder.restored(MoveFlowSnapshotCodec.decode(MoveFlowSnapshotCodec.encode(h.snapshot())))

        assertEquals(MoveFlowStep.SELECT_TARGET, r.step)
        assertEquals(MoveVerb.TRANSFER, r.verb)
        assertEquals("目标主题", r.transferTarget?.displayName)
        assertEquals(1795057663684L, r.transferTarget?.createdAt)
        assertEquals("条目计数用快照值（禁 0 占位——F1-C1 教训）", 7, r.transferTarget?.entryCount)
        assertEquals("命名草稿", r.newTopicInput) // 关窗不丢字（修订⑤）
        assertTrue(r.failed)
        assertFalse(r.restoredFromExecuting)
    }

    @Test
    fun executingRestored_asFailedRetryFace_pendingReDecision() {
        val h = MoveFlowHolder()
        h.start(MoveVerb.MOVE_OUT, MoveFlowStep.CONFIRM)
        h.markExecuting(true)

        val r = MoveFlowHolder.restored(MoveFlowSnapshotCodec.decode(MoveFlowSnapshotCodec.encode(h.snapshot())))

        // 暂记重试面貌＋一次性标记（真实结局待重载重推导）
        assertTrue("executing 恢复＝暂记 failed", r.failed)
        assertTrue("一次性重推导标记就位", r.restoredFromExecuting)
        assertEquals(MoveFlowStep.CONFIRM, r.step)
        r.consumeRestoredFromExecuting()
        assertFalse(r.restoredFromExecuting)
    }

    @Test
    fun moveOut_nullTarget_roundTrip() {
        val h = MoveFlowHolder()
        h.start(MoveVerb.MOVE_OUT, MoveFlowStep.CONFIRM) // 移出方向目标恒 null（主文件无选择框）
        val r = MoveFlowHolder.restored(MoveFlowSnapshotCodec.decode(MoveFlowSnapshotCodec.encode(h.snapshot())))
        assertEquals(MoveVerb.MOVE_OUT, r.verb)
        assertNull(r.transferTarget)
        assertFalse(r.restoredFromExecuting)
    }

    @Test
    fun closedSnapshot_restoresClosed() {
        val h = MoveFlowHolder()
        h.start(MoveVerb.MOVE_IN, MoveFlowStep.CONFIRM)
        h.newTopicInput = "保留到关窗前"
        h.close()
        val r = MoveFlowHolder.restored(MoveFlowSnapshotCodec.decode(MoveFlowSnapshotCodec.encode(h.snapshot())))
        assertNull(r.step)
        assertNull(r.verb)
        assertNull(r.transferTarget)
        // 关窗不清命名草稿（修订⑤）——快照恢复同语义
        assertEquals("保留到关窗前", r.newTopicInput)
    }

    @Test
    fun corruptedEncoding_restoresClosed() {
        val r = MoveFlowHolder.restored(MoveFlowSnapshotCodec.decode(arrayListOf("v1")))
        assertNull("缺字段＝损坏→关闭态（安全默认）", r.step)
        assertFalse(r.failed)
        assertFalse(r.restoredFromExecuting)
    }
}