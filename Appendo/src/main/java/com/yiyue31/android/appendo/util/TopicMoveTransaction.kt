package com.yiyue31.android.appendo.util

import com.yiyue31.android.appendo.util.MoveResult.SourceDeleteFailed
import com.yiyue31.android.appendo.util.MoveResult.SourceReadFailed
import com.yiyue31.android.appendo.util.MoveResult.Success
import com.yiyue31.android.appendo.util.MoveResult.TargetReadFailed
import com.yiyue31.android.appendo.util.MoveResult.TargetWriteFailed

/**
 * 移入组合事务（设计 §6.4，需求 84）：单次持 [FileOperationLock] 的「先写目标、后删源」编排。
 * 纯 util 不碰 Android UI；目标恒为 FileBasedMarkdownFile（topics/ 私有目录，方案 A）。
 */

/**
 * 移入组合事务结果（需求 84(b) 失败语义精确化）。
 *
 * 失败类不带 reason 载荷：每类在 UI 映射唯一文案（设计 §9），无消费方读 reason；
 * 计数载荷口径统一——凡能给出计数的态（[Success] / [SourceDeleteFailed]）三字段齐全，
 * N（所选）= moved + skippedExisting + missingFromSource 恒可核对（需求 88）。
 */
sealed interface MoveResult {

    /**
     * 成功：moved=写入目标数；skippedExisting=目标已存在跳过数（需求 87 透明）；
     * missingFromSource=所选但源中未找到数（正常恒 0，防御口径）。
     */
    data class Success(val moved: Int, val skippedExisting: Int, val missingFromSource: Int) : MoveResult

    /** 源读取失败（典型：SAF 授权失效）。目标与源均未改动——干净失败，无任何中间态。 */
    object SourceReadFailed : MoveResult

    /** 目标读取失败。源未改动（目标在私有目录，极罕见）。 */
    object TargetReadFailed : MoveResult

    /** 写入段失败（84b）：目标可能已部分写入（不回滚），当前文件原样保留。可整体重试。 */
    object TargetWriteFailed : MoveResult

    /**
     * 删除段失败（84b）：目标已写全、当前文件可能部分移除——「两边都有」软中间态；
     * 重试幂等收敛（87：目标已存在 → skipped，源再删）。计数口径与 [Success] 相同。
     */
    data class SourceDeleteFailed(val moved: Int, val skippedExisting: Int, val missingFromSource: Int) : MoveResult
}

/**
 * 故障注入开关（设计 §10.1 单测层失败分支）：生产调用一律缺省 [NONE]。
 * 本参数**仅供测试**（项目未使用 androidx.annotation，以本 KDoc 注明代替 @VisibleForTesting）。
 */
enum class MoveFaults {
    NONE,

    /** 目标写段强制失败：目标不写、源不动（源文件逐字节不变）→ [MoveResult.TargetWriteFailed]。 */
    FAIL_TARGET_WRITE,

    /** 源删段强制失败：目标已写全（「两边都有」）→ [MoveResult.SourceDeleteFailed]。 */
    FAIL_SOURCE_DELETE
}

object TopicMoveTransaction {

    /**
     * 移入组合事务（需求 84(a)）：单次持 [FileOperationLock]，先写目标、后删源，全程无锁间隙
     * （synchronized 可重入——内部调 source/target 既有方法不释放锁，禁止两段分离持锁）。
     *
     * 六步编排（设计 §6.1），各步以 runCatching 包裹，IOException / RuntimeException 等一切
     * 异常映射到所在段失败类：
     * 1. 源读 source.readAllWithStatus（→ [MoveResult.SourceReadFailed]，源目标均未动）；
     * 2. EntryParser.parse 取权威条目集（UI 只传 keys，内容以锁内源读取为准；→ SourceReadFailed）；
     * 3. 目标读（exists() 判定；不存在视作空文档、以 [MarkdownFormatter.FILE_HEADER] 起步——
     *    懒创建兜底；→ [MoveResult.TargetReadFailed]，源未动）；
     * 4. 去重计算：toAdd = 所选 − 目标已有；skipped = 所选 ∩ 目标已有（87；→ TargetReadFailed）；
     * 5. 目标写 target.writeAll(目标全文 + toAdd 升序拼接)（→ [MoveResult.TargetWriteFailed]）。
     *    特别地：FileBasedMarkdownFile.atomicWrite 的 `File.createTempFile` 位于其 `return try`
     *    之外（目标目录只读时抛**未捕获异常**）——由本段 runCatching 捕获映射 TargetWriteFailed
     *    （89(c) 注入正落此分支）；**勿改既有 atomicWrite 的异常边界**（R11 不扩大改动面）；
     * 6. 源删 source.deleteEntries(keys)（→ [MoveResult.SourceDeleteFailed]）。T-02 实现已把
     *    一切异常吞为 null 返回（null ⟺ 操作未发生），null 即映射 SourceDeleteFailed；
     *    runCatching 仍包以防万一。
     *
     * 事务边界（设计 §6.4「两实现的事务边界与失败态」表）：
     * - 源读失败：零副作用（FileBased / SAF 同）；SAF 授权失效场景上层叠加 TD-021 文案；
     * - 目标读/写失败：源未动（目标恒 FileBased，无 SAF 目标分支）；目标写常规失败不回滚、
     *   可整体重试（atomicWrite rename 失败退化覆写分支的截断风险为既有系统性风险，R11 披露）；
     * - 源删失败：目标已写全＝「两边都有」软中间态，重试幂等收敛；崩溃中断要么旧要么新
     *   （84(g)：中断允许「两边都有」，永不「两边都没有」）。
     *
     * 语义细则：
     * - 目标写入不新增接口方法（设计 §6.3 注）：目标全文 = 目标读内容 + toAdd 按时间戳字符串
     *   升序的 [EntryParser.format] 拼接串，一次 writeAll；末尾衔接镜像 appendEntryInternal
     *   语义（空白/不存在起步形态 = FILE_HEADER + 首条），原时间戳原样（appendEntry 语义）；
     * - toAdd 为空（全 skipped / missing）不写目标——镜像 deleteEntries 无命中早退，免一次
     *   无谓全文件写；此时 FAIL_TARGET_WRITE 注入不触发（无写入段可失败）；
     * - 去重 key 与归档恢复同源（timestamp|content，[EntryKey.of] 的 parse 产物语义）；
     * - 计数口径：N（所选 keys 数）= moved + skippedExisting + missingFromSource 恒可核对；
     *   missing 优先归类（所选不在源即 missing，不论目标是否已有）；
     * - 任一失败态不得出现「两边都没有」（84 硬约束：写段失败源未删、删段失败目标已写全）。
     *
     * @param source 当前文件的 MarkdownFileOperations（FileBased 或 Saf）
     * @param target 主题文档，恒为 FileBasedMarkdownFile（topics/ 私有目录，方案 A）
     * @param keys 所选条目标识；条目内容以锁内源读取为准
     * @param faults 故障注入（**仅供测试**，生产恒缺省 [MoveFaults.NONE]）
     */
    fun move(
        source: MarkdownFileOperations,
        target: MarkdownFileOperations,
        keys: Set<EntryKey>,
        faults: MoveFaults = MoveFaults.NONE
    ): MoveResult = synchronized(FileOperationLock) {

        // —— 步骤 1：源读段（异常/读失败 → SourceReadFailed，源目标均未动）——
        val sourceRead = runCatching { source.readAllWithStatus() }
            .getOrElse { return SourceReadFailed }
        if (sourceRead.failed) return SourceReadFailed

        // —— 步骤 2：锁内取权威条目集（异常 → SourceReadFailed）——
        val sourceEntries = runCatching { EntryParser.parse(sourceRead.content) }
            .getOrElse { return SourceReadFailed }
        val selected = sourceEntries.filter { EntryKey.of(it) in keys }

        // —— 步骤 3：目标读段（不存在视作空文档 FILE_HEADER 起步；异常/读失败 → TargetReadFailed，源未动）——
        val targetRead = runCatching { if (target.exists()) target.readAllWithStatus() else null }
        if (targetRead.isFailure) return TargetReadFailed
        val readResult = targetRead.getOrNull()
        if (readResult?.failed == true) return TargetReadFailed
        // 不存在 → 空文档；存在但空白 → 同 appendEntryInternal 空白起步语义，均以 FILE_HEADER 起步
        val targetContent =
            if (readResult == null || readResult.content.isBlank()) MarkdownFormatter.FILE_HEADER
            else readResult.content

        // —— 步骤 4：去重计算（87 透明幂等；目标 parse 异常 → TargetReadFailed）——
        val targetKeys = runCatching { EntryParser.parse(targetContent).map { EntryKey.of(it) }.toSet() }
            .getOrElse { return TargetReadFailed }
        val selectedKeys = selected.map { EntryKey.of(it) }.toSet()
        val missingCount = keys.size - selectedKeys.size // 所选不在源（优先归类，不论目标是否已有）
        val skippedCount = selectedKeys.intersect(targetKeys).size
        // 同 key 多条完全重复条目（历史数据理论存在）收敛为一次写入；deleteEntries 侧全删（幂等彻底）
        val toAdd = selected.distinctBy { EntryKey.of(it) }.filter { EntryKey.of(it) !in targetKeys }

        // —— 步骤 5：目标写段（toAdd 空＝免写；注入/异常/写失败 → TargetWriteFailed）——
        if (toAdd.isNotEmpty()) {
            val written = runCatching {
                if (faults == MoveFaults.FAIL_TARGET_WRITE) error("injected: FAIL_TARGET_WRITE")
                // 拼接镜像 appendEntryInternal：现有内容 + 升序条目块（原时间戳原样，ZWSP 隔离经 format）
                val appended = toAdd.sortedBy { it.rawTimestamp }.joinToString("") {
                    EntryParser.format(it.rawTimestamp, it.content)
                }
                target.writeAll(targetContent + appended)
            }.getOrElse { return TargetWriteFailed }
            if (!written) return TargetWriteFailed
        }

        // —— 步骤 6：源删段（注入/异常 → null；deleteEntries null ⟺ 操作未发生 → SourceDeleteFailed）——
        val deleted = runCatching {
            if (faults == MoveFaults.FAIL_SOURCE_DELETE) error("injected: FAIL_SOURCE_DELETE")
            source.deleteEntries(keys)
        }.getOrElse { null }
        if (deleted == null) return SourceDeleteFailed(toAdd.size, skippedCount, missingCount)

        Success(toAdd.size, skippedCount, missingCount)
    }
}
