package com.yiyue31.android.appendo.ui

// JVM-TESTABLE: import 白名单＝{kotlin.*, java.*, androidx.compose.runtime.*}（纯 JVM 多平台）——
// android.*(framework: Bundle/Parcelable/Uri/Log 等) 禁入：isReturnDefaultValues=true 下 framework
// API 在 JVM 测试静默返回默认值＝假绿陷阱；Bundle 适配由 rememberSaveable 框架承担（真机验证）。
// （TD-051/N-4 设计 §3.1 红线，2026-11-19）

import androidx.compose.runtime.saveable.Saver
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.util.EntryKey

/**
 * 状态编码原语（TD-051/N-4，设计 §3.1）：版本头＋长度前缀字段序列。
 *
 * - 每字段＝两个元素 `[len, raw]`；`len`＝十进制字符数（≥0）或 [NULL] 标记（字段为 null）；
 * - 长度前缀 ⇒ 任意用户文本无损（换行/分隔符/unicode/ZWSP/`\u0000` 均无碰撞面）；
 * - **字段序纪律＝只追加末尾**＋消费方按位置解读——新增字段须 bump 版本头或追加至尾部并容忍旧快照
 *   缺尾（本文件各 Codec 均按「至少 N 字段，多余容忍」解码——前向兼容）；
 * - decode 健壮性：版本不符/奇数余项/非数字长度/负数/长度不符 → null（安全默认），绝不崩溃不臆造。
 */
internal object StateCodec {
    const val VERSION = "v1"

    /** 字段级 null 标记（占 len 位；"-1" 不作标记——负数一律视为损坏）。 */
    const val NULL = "N"

    fun encode(fields: List<String?>): ArrayList<String> {
        val out = ArrayList<String>(fields.size * 2 + 1)
        out.add(VERSION)
        for (f in fields) {
            if (f == null) {
                out.add(NULL)
                out.add("")
            } else {
                out.add(f.length.toString())
                out.add(f)
            }
        }
        return out
    }

    /** 损坏（含版本不符）→ null；多余尾字段容忍（前向兼容——只追加纪律的旧读者侧）。 */
    fun decode(raw: List<String>): List<String?>? {
        if (raw.isEmpty() || raw[0] != VERSION) return null
        val out = ArrayList<String?>(raw.size / 2)
        var i = 1
        while (i < raw.size) {
            if (i + 1 >= raw.size) return null // 奇数余项＝截断
            val lenField = raw[i]
            if (lenField == NULL) {
                out.add(null)
            } else {
                val len = lenField.toIntOrNull() ?: return null // 非数字长度
                if (len < 0) return null                          // 负长度
                val value = raw[i + 1]
                if (value.length != len) return null              // 长度不符
                out.add(value)
            }
            i += 2
        }
        return out
    }
}

/**
 * 选中集容量守卫上限（TD-051 设计 §3.5）：估算编码字节超过即**确定性截断**（排序序保留可容者；
 * 2026-11-19 实施修正：原指纹匹配方案在 Saver restore 时序不可行——entries 未加载，两阶段接线
 * 引入全局 pending 面＝更劣债务）。宁丢部分选中不丢草稿不崩溃（Bundle ~500KB 事务上限防护）。
 */
internal const val MAX_SELECTION_ENCODED_CHARS = 100_000 // ~200KB UTF-16

/**
 * `Set<EntryKey>` Saver：**按 (timestamp, content) 排序后编码**（同快照两次 encode 逐字节相等
 * ——确定性纪律）＋容量守卫截断。编码＝交错 ts/content 字段＋两尾字段（实留键数/截断 0-1 标记）
 * ——全部经 [StateCodec] 成对方案（无裸尾元素，decode 配对律不破）。损坏 → null（restore 语义＝
 * 保持初值 emptySet——宁丢选中）。
 */
internal val EntryKeySetSaver: Saver<Set<EntryKey>, ArrayList<String>> = Saver(
    save = { keys ->
        val sorted = keys.sortedWith(compareBy({ it.timestamp }, { it.content }))
        val fields = ArrayList<String?>(sorted.size * 2 + 2)
        var estChars = 0
        var kept = 0
        var truncated = false
        for (k in sorted) {
            val size = k.timestamp.length + k.content.length
            if (estChars + size > MAX_SELECTION_ENCODED_CHARS) {
                truncated = true
                break
            }
            estChars += size
            fields.add(k.timestamp)
            fields.add(k.content)
            kept++
        }
        fields.add(kept.toString())              // 尾字段1：实留键数（截断时＜全集——恢复值即确定性子集）
        fields.add(if (truncated) "1" else "0")  // 尾字段2：截断标记（前向兼容＝多余尾字段容忍）
        StateCodec.encode(fields)
    },
    restore = { raw ->
        val f = StateCodec.decode(raw) ?: return@Saver null
        if (f.size < 2) return@Saver null
        val bodySize = f.size - 2
        if (bodySize % 2 != 0) return@Saver null
        val keys = HashSet<EntryKey>(bodySize / 2)
        var i = 0
        while (i < bodySize) {
            val ts = f[i] ?: return@Saver null
            val content = f[i + 1] ?: return@Saver null
            keys.add(EntryKey(ts, content))
            i += 2
        }
        keys
    }
)

/** `LinkEntry?` Saver：null ↔ (ts, content) 双字段编码往返。 */
internal val LinkEntrySaver: Saver<LinkEntry?, ArrayList<String>> = Saver(
    save = { e -> StateCodec.encode(listOf(e?.timestamp, e?.content)) },
    restore = { raw ->
        val f = StateCodec.decode(raw) ?: return@Saver null
        if (f.size < 2) return@Saver null
        val ts = f[0]
        val content = f[1]
        if (ts == null || content == null) null else LinkEntry(ts, content)
    }
)

/** 可空字符串 Saver：空串＝null 哨兵（适用非空语义字段——如 lastEditedTs 的时间戳串恒非空）。 */
internal val NullableStringSaver: Saver<String?, String> = Saver(
    save = { it ?: "" },
    restore = { it.ifEmpty { null } }
)

/** [MoveFlowSnapshot] 编解码（字段序＝快照属性序——只追加纪律）。 */
internal object MoveFlowSnapshotCodec {
    /** 全 null 槽位＝「流程关闭」快照（step/verb/target 均 null）。 */
    val CLOSED_FIELDS = listOf<String?>(null, null, null, null, null, null, "", "false", "false")

    fun encode(s: MoveFlowSnapshot): ArrayList<String> = StateCodec.encode(
        listOf(
            s.step?.name,
            s.verb?.name,
            s.targetPath,
            s.targetName,
            s.targetCreatedAt.takeIf { s.targetPath != null }?.toString(),
            s.targetEntryCount.takeIf { s.targetPath != null }?.toString(),
            s.newTopicInput,
            s.failed.toString(),
            s.executing.toString()
        )
    )

    fun decode(raw: List<String>): MoveFlowSnapshot {
        val f = StateCodec.decode(raw) ?: return MoveFlowSnapshot.CLOSED
        if (f.size < 9) return MoveFlowSnapshot.CLOSED // 缺字段＝损坏→关闭态
        val step = f[0]?.let { name -> runCatching { MoveFlowStep.valueOf(name) }.getOrNull() }
        val verb = f[1]?.let { name -> runCatching { MoveVerb.valueOf(name) }.getOrNull() }
        val targetPath = f[2]
        return MoveFlowSnapshot(
            step = step,
            verb = verb,
            targetPath = targetPath,
            targetName = f[3],
            targetCreatedAt = if (targetPath == null) 0L else f[4]?.toLongOrNull() ?: 0L,
            targetEntryCount = if (targetPath == null) 0 else f[5]?.toIntOrNull() ?: 0,
            newTopicInput = f[6] ?: "",
            failed = f[7]?.toBooleanStrictOrNull() ?: false,
            executing = f[8]?.toBooleanStrictOrNull() ?: false
        )
    }
}

/** `TopicDocument?` Saver（TopicListScreen 改名目标——N-4 第四丢失面；entryCount 用快照值）。 */
internal val TopicDocumentSaver: Saver<TopicDocument?, ArrayList<String>> = Saver(
    save = { t ->
        StateCodec.encode(
            listOf(
                t?.file?.path,
                t?.displayName,
                t?.createdAt?.toString(),
                t?.entryCount?.toString()
            )
        )
    },
    restore = { raw ->
        val f = StateCodec.decode(raw) ?: return@Saver null
        if (f.size < 4 || f[0] == null || f[1] == null || f[2] == null || f[3] == null) null
        else TopicDocument(
            file = java.io.File(f[0]!!),
            displayName = f[1]!!,
            createdAt = f[2]!!.toLongOrNull() ?: 0L,
            entryCount = f[3]!!.toIntOrNull() ?: 0
        )
    }
)

/** [MoveFlowHolder] Saver：save＝snapshot() 现值（保存时点闭包读取）；restore＝restored() 工厂。 */
internal val MoveFlowHolderSaver: Saver<MoveFlowHolder, ArrayList<String>> = Saver(
    save = { holder -> MoveFlowSnapshotCodec.encode(holder.snapshot()) },
    restore = { raw -> MoveFlowHolder.restored(MoveFlowSnapshotCodec.decode(raw)) }
)