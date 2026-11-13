package com.yiyue31.android.appendo.util

import com.yiyue31.android.appendo.util.MoveResult.SameDocument
import com.yiyue31.android.appendo.util.MoveResult.SourceDeleteFailed
import com.yiyue31.android.appendo.util.MoveResult.SourceReadFailed
import com.yiyue31.android.appendo.util.MoveResult.Success
import com.yiyue31.android.appendo.util.MoveResult.TargetReadFailed
import com.yiyue31.android.appendo.util.MoveResult.TargetWriteFailed

/**
 * 移入/移出/转移组合事务（设计 §6.4＋§21.1，需求 84/96）：单次持 [FileOperationLock] 的
 * 「先写目标、后删源」编排。**通用化（戊批次 T-20）**：源与目标均可为 FileBased 或 SAF
 * ——移入＝move(主, 主题)；移出＝move(主题, 主)；转移＝move(主题A, 主题B)。
 * 纯 util 不碰 Android UI。
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

    /**
     * 源与目标为同一文档（戊批次 T-20，需求 96 SameDocument 护栏）：引用相等，或双 FileBased
     * 且 canonicalPath 相同。零副作用、无任何读写。正常 UI 不可达（目标列表排除当前主题），
     * 纯防御——A→A 场景下若放行会「全 skipped＋步骤 6 无条件删源」＝条目删光却报成功。
     *
     * **已知局限（code-review 修正注记，TD 登记）**：FileBased＋SAF 混合形态无法在此层判定
     * 同一物理文件（SAF uri 不透明，无可靠路径比对）——触发前提＝用户把 topics/ 目录内文件
     * 经 SAF 选为主文档（系统 picker 正常不暴露应用私有目录，属病态配置）。若该形态需硬防护，
     * 须在 SAF 选文件入口做路径排除，而非本事务层。
     */
    object SameDocument : MoveResult

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
     * 移入/移出/转移组合事务（需求 84(a)＋96）：单次持 [FileOperationLock]，先写目标、后删源，
     * 全程无锁间隙（synchronized 可重入——内部调 source/target 既有方法不释放锁，禁止两段分离持锁）。
     *
     * 六步编排（设计 §6.1＋§21.1 通用化），各步以 runCatching 包裹，IOException / RuntimeException
     * 等一切异常映射到所在段失败类：
     * 0. **SameDocument 护栏**（96）：`source === target` 或双 FileBased 且 canonicalPath 相同
     *    → [MoveResult.SameDocument] 零副作用（正常 UI 不可达——目标列表排除当前主题，纯防御）；
     * 1. 源读 source.readAllWithStatus（→ [MoveResult.SourceReadFailed]，源目标均未动）；
     * 2. EntryParser.parse 取权威条目集（UI 只传 keys，内容以锁内源读取为准；→ SourceReadFailed）；
     * 3. 目标读——**按 [MarkdownFileOperations.existsCheckDefinitive] 分派**（设计 §21.1/T-20）：
     *    definitive=true（FileBased）走 exists() 判定，不存在视作空文档、以 [MarkdownFormatter.FILE_HEADER]
     *    起步（懒创建兜底）；definitive=false（SAF）**不查 exists、直接 readAllWithStatus**——
     *    SAF 的 exists() 吞 SecurityException 返 false（授权失效≠不存在），照 exists 走会在授权
     *    失效时误当「不存在」起步覆写；读失败/授权失效→ [MoveResult.TargetReadFailed]（源未动）；
     * 4. 去重计算：toAdd = 所选 − 目标已有；skipped = 所选 ∩ 目标已有（87；→ TargetReadFailed）；
     * 5. 目标写 target.writeAll(目标全文 + toAdd 升序拼接)（→ [MoveResult.TargetWriteFailed]）。
     *    特别地：FileBasedMarkdownFile.atomicWrite 的 `File.createTempFile` 位于其 `return try`
     *    之外（目标目录只读时抛**未捕获异常**）——由本段 runCatching 捕获映射 TargetWriteFailed
     *    （89(c) 注入正落此分支）；**勿改既有 atomicWrite 的异常边界**（R11 不扩大改动面）；
     * 6. 源删 source.deleteEntries(keys)（→ [MoveResult.SourceDeleteFailed]）。T-02 实现已把
     *    一切异常吞为 null 返回（null ⟺ 操作未发生），null 即映射 SourceDeleteFailed；
     *    runCatching 仍包以防万一。
     *
     * 事务边界（设计 §6.4「两实现的事务边界与失败态」表＋§21.1 通用化）：
     * - 源读失败：零副作用（FileBased / SAF 同）；SAF 授权失效场景上层叠加 TD-021 文案；
     * - 目标读/写失败：源未动；SAF 目标（移出方向）读失败含授权失效、写常规失败不回滚、
     *   可整体重试（SAF 软恢复语义由 SafMarkdownFile 自承）；
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
     * @param source 源文档（主文件或主题，FileBased 或 Saf）
     * @param target 目标文档（主题或主文件，FileBased 或 Saf；与源不可为同一文档）
     * @param keys 所选条目标识；条目内容以锁内源读取为准
     * @param faults 故障注入（**仅供测试**，生产恒缺省 [MoveFaults.NONE]）
     */
    fun move(
        source: MarkdownFileOperations,
        target: MarkdownFileOperations,
        keys: Set<EntryKey>,
        faults: MoveFaults = MoveFaults.NONE
    ): MoveResult = synchronized(FileOperationLock) {

        // —— 步骤 0：SameDocument 护栏（96；零副作用——正常 UI 不可达，纯防御）——
        if (source === target) return SameDocument
        if (source is FileBasedMarkdownFile && target is FileBasedMarkdownFile &&
            runCatching { source.file.canonicalPath == target.file.canonicalPath }.getOrDefault(false)
        ) {
            return SameDocument
        }

        // —— 步骤 1：源读段（异常/读失败 → SourceReadFailed，源目标均未动）——
        val sourceRead = runCatching { source.readAllWithStatus() }
            .getOrElse { return SourceReadFailed }
        if (sourceRead.failed) return SourceReadFailed

        // —— 步骤 2：锁内取权威条目集（异常 → SourceReadFailed）——
        val sourceEntries = runCatching { EntryParser.parse(sourceRead.content) }
            .getOrElse { return SourceReadFailed }
        val selected = sourceEntries.filter { EntryKey.of(it) in keys }

        // —— 步骤 3：目标读段——按 existsCheckDefinitive 分派（T-20，设计 §21.1）：
        // FileBased（definitive）维持 exists() 分支（不存在→懒创建起步，防 FileNotFound 误判失败）；
        // SAF（非 definitive）不查 exists、直接读——授权失效≠不存在（exists 吞 SecurityException），
        // 读失败→TargetReadFailed 干净失败（防误当「不存在」起步覆写）。SAF 目标＝主文件必已存在。
        val targetRead = runCatching {
            if (target.existsCheckDefinitive) {
                if (target.exists()) target.readAllWithStatus() else null
            } else {
                target.readAllWithStatus()
            }
        }
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
