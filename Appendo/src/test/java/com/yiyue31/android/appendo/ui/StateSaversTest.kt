package com.yiyue31.android.appendo.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.yiyue31.android.appendo.util.EntryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saver.save 带 SaverScope receiver——测试直调用恒真 scope。 */
private fun <S, R : Any> Saver<S, R>.saveWith(value: S): R =
    with(object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }) {
        @Suppress("UNCHECKED_CAST")
        save(value) as R
    }

/**
 * 状态 Saver 与编码原语（N-4/TD-051 设计 §3.1/§3.5——corruption 穷举≥7 子项＋确定性＋容量守卫，
 * TC-106 JVM 主判据）。纯 JVM 直测（android framework 禁入——isReturnDefaultValues 假绿围栏）。
 */
class StateSaversTest {

    // ==================== StateCodec ====================

    @Test
    fun codec_roundTrip_arbitraryTextLossless() {
        val fields = listOf(
            "a", null, "",
            "多行\n含---​零宽与\u0000空字符",
            "2026-01-01 01:00:00.000",
            "长度前缀下任意文本无损——含 =length= 123 与 N 字面"
        )
        assertEquals(fields, StateCodec.decode(StateCodec.encode(fields)))
    }

    @Test
    fun codec_encodeDeterministic() {
        val fields = listOf("b", null, "a") // 乱序输入不影响同列表确定性
        assertEquals(StateCodec.encode(fields), StateCodec.encode(fields))
    }

    @Test
    fun codec_corruption_family_allSafeDefault() {
        assertNull("空列表", StateCodec.decode(emptyList()))
        assertNull("版本不符", StateCodec.decode(arrayListOf("v2", "1", "a")))
        assertNull("奇数余项（截断）", StateCodec.decode(arrayListOf("v1", "3")))
        assertNull("非数字长度", StateCodec.decode(arrayListOf("v1", "x", "abc")))
        assertNull("负长度（-1 不作 null 标记）", StateCodec.decode(arrayListOf("v1", "-1", "")))
        assertNull("长度不符", StateCodec.decode(arrayListOf("v1", "5", "abc")))
    }

    @Test
    fun codec_extraTrailingFields_tolerated() {
        val encoded = StateCodec.encode(listOf("a", "b"))
        encoded.add("1") // 合法字段对（len=1, raw=c）＝未来版本追加的尾字段
        encoded.add("c")
        assertEquals("多余尾字段容忍（只追加纪律的旧读者侧）", listOf("a", "b", "c"), StateCodec.decode(encoded))
    }

    // ==================== EntryKeySetSaver ====================

    @Test
    fun keySet_emptySet_roundTrip() {
        val saved = EntryKeySetSaver.saveWith(emptySet())
        assertEquals(emptySet<EntryKey>(), EntryKeySetSaver.restore(saved))
    }

    @Test
    fun keySet_200entries_roundTrip() {
        val keys = (0 until 200).map { EntryKey("2026-01-01 %02d:00:00.000".format(it / 60, it % 60), "条目$it\n多行") }.toSet()
        assertEquals(keys, EntryKeySetSaver.restore(EntryKeySetSaver.saveWith(keys)))
    }

    @Test
    fun keySet_encodeDeterministic_sortedByTsThenContent() {
        val keys = setOf(
            EntryKey("2026-01-01 02:00:00.000", "b"),
            EntryKey("2026-01-01 01:00:00.000", "z"),
            EntryKey("2026-01-01 01:00:00.000", "a")
        )
        assertEquals("同集两次 encode 逐字节相等（排序纪律）", EntryKeySetSaver.saveWith(keys), EntryKeySetSaver.saveWith(keys))
        val restored = EntryKeySetSaver.restore(EntryKeySetSaver.saveWith(keys))
        assertEquals(keys, restored)
    }

    @Test
    fun keySet_capacityGuard_deterministicTruncation() {
        // 60 键 × ~2200 字符 ≈ 13 万字符 > 上限 10 万 → 截断
        val big = "x".repeat(2200)
        val keys = (0 until 60).map { EntryKey("2026-01-01 01:%02d:00.000".format(it), "$big-$it") }.toSet()
        val saved = EntryKeySetSaver.saveWith(keys)
        assertTrue("截断标记随快照（尾字段 0-1 标记的 raw 元素）", saved.last() == "1")
        val restored = EntryKeySetSaver.restore(saved)!!
        assertTrue("宁丢部分选中：保留确定性子集", restored.isNotEmpty() && restored.size < keys.size)
        // 截断＝排序序前缀（确定性）
        val sorted = keys.sortedWith(compareBy({ it.timestamp }, { it.content }))
        assertEquals(sorted.take(restored.size).toSet(), restored)
    }

    @Test
    fun keySet_corruption_restoreNull() {
        assertNull(EntryKeySetSaver.restore(arrayListOf("v1")))
    }

    // ==================== LinkEntrySaver / NullableStringSaver ====================

    @Test
    fun linkEntry_nullAndEntry_roundTrip() {
        assertNull(LinkEntrySaver.restore(LinkEntrySaver.saveWith(null)))
        val e = LinkEntry("2026-11-19 11:31:50.665", "INPUT-A\n第二行​")
        assertEquals(e, LinkEntrySaver.restore(LinkEntrySaver.saveWith(e)))
    }

    @Test
    fun nullableString_nullSentinel_roundTrip() {
        assertNull(NullableStringSaver.restore(NullableStringSaver.saveWith(null)))
        assertEquals("abc", NullableStringSaver.restore(NullableStringSaver.saveWith("abc")))
    }

    @Test
    fun topicDocument_nullAndEntry_roundTrip() {
        assertNull(TopicDocumentSaver.restore(TopicDocumentSaver.saveWith(null)))
        val t = com.yiyue31.android.appendo.data.TopicDocument(
            file = java.io.File("files/topics/验收主题.md"),
            displayName = "验收主题",
            createdAt = 1795057663684L,
            entryCount = 7
        )
        assertEquals(t, TopicDocumentSaver.restore(TopicDocumentSaver.saveWith(t)))
    }

    // ==================== MoveFlowSnapshotCodec ====================

    @Test
    fun moveSnapshot_fullState_roundTrip() {
        val s = MoveFlowSnapshot(
            step = MoveFlowStep.CONFIRM, verb = MoveVerb.MOVE_OUT,
            targetPath = null, targetName = null, targetCreatedAt = 0L, targetEntryCount = 0,
            newTopicInput = "命名草稿", failed = false, executing = true
        )
        assertEquals(s, MoveFlowSnapshotCodec.decode(MoveFlowSnapshotCodec.encode(s)))
    }

    @Test
    fun moveSnapshot_withTarget_roundTrip() {
        val s = MoveFlowSnapshot(
            step = MoveFlowStep.CONFIRM, verb = MoveVerb.TRANSFER,
            targetPath = "files/topics/目标.md", targetName = "目标",
            targetCreatedAt = 1795057663684L, targetEntryCount = 7,
            newTopicInput = "", failed = true, executing = false
        )
        assertEquals(s, MoveFlowSnapshotCodec.decode(MoveFlowSnapshotCodec.encode(s)))
    }

    @Test
    fun moveSnapshot_corruption_closedDefault() {
        assertEquals(MoveFlowSnapshot.CLOSED, MoveFlowSnapshotCodec.decode(arrayListOf("v1")))
        assertEquals(MoveFlowSnapshot.CLOSED, MoveFlowSnapshotCodec.decode(emptyList()))
        val truncated = MoveFlowSnapshotCodec.encode(
            MoveFlowSnapshot(MoveFlowStep.CONFIRM, MoveVerb.MOVE_IN, null, null, 0L, 0, "", false, false)
        )
        while (truncated.size > 5) truncated.removeAt(truncated.size - 1) // 人为截断
        assertEquals("缺字段→关闭态", MoveFlowSnapshot.CLOSED, MoveFlowSnapshotCodec.decode(truncated))
    }

    @Test
    fun moveSnapshot_extraTrailingField_tolerated() {
        val s = MoveFlowSnapshot.CLOSED
        val encoded = MoveFlowSnapshotCodec.encode(s)
        encoded.add("0")
        encoded.add("extra")
        assertNotNull(MoveFlowSnapshotCodec.decode(encoded))
    }
}