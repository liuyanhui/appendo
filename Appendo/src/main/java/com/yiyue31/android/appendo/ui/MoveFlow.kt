package com.yiyue31.android.appendo.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.util.MoveResult

/**
 * 移出/移入/转移共享流程（戊批次 T-26，设计 §21.2/§22.6 评审 E7）：[MoveFlowHolder] 状态
 * ＋[MoveOutcomeMapping] 决策映射纯函数。
 *
 * **统一迁移（己批次 WA-1，2026-11-18）**：MainScreen 移入流程迁入本 holder——三动词
 * （移入/移出/转移）单一状态机＋Mapping 全组合；MainScreen 与工作区两消费方。
 *
 * 执行编排纪律（沿 MainScreen.executeMove 先例）：事务在 Dispatchers.IO；成功→统一失效
 * 规则 invalidate(源＋目标)（§20.2）＋本页重载；失败→对话框保持变「重试」、保持勾选与目标
 * （84(b) 幂等收敛）；SameDocument＝防御路径只提示不动上下文（96）。
 */

/** 流程动词（WA-1 起三动词全量：移入/移出/转移）。 */
internal enum class MoveVerb { MOVE_IN, MOVE_OUT, TRANSFER }

/**
 * 流程状态 holder（对话框栈驱动见 [MoveFlowStep]）：
 * [step] null＝未开启；[verb] 记录发起动词（确认句/产出映射分场景）；[transferTarget]＝
 * 目标主题（**移入/转移**共用——移入＝所选主题；移出方向恒 null，目标＝主文件无选择对话框）；
 * 新建子流程输入 [newTopicInput] 由本 holder 持有（D2：创建成功即为目标；取消路径输入保留
 * ——草稿保全先例，修订⑤）。
 */
internal class MoveFlowHolder {
    var step by mutableStateOf<MoveFlowStep?>(null)
        private set
    var verb by mutableStateOf<MoveVerb?>(null)
        private set
    var transferTarget by mutableStateOf<TopicDocument?>(null)
        private set
    var newTopicInput by mutableStateOf("")
    var executing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** 发起（verb 与首步一次设定；转移＝SELECT_TARGET，移出＝直达 CONFIRM）。 */
    fun start(verb: MoveVerb, step: MoveFlowStep) {
        this.verb = verb
        this.step = step
    }

    fun goTo(step: MoveFlowStep) {
        this.step = step
    }

    fun selectTarget(target: TopicDocument?) {
        transferTarget = target
    }

    fun markExecuting(value: Boolean) {
        executing = value
    }

    fun markFailed() {
        failed = true
    }

    /** 进确认框前清失败态（换目标/重开时重置「重试」面貌）。 */
    fun clearFailed() {
        failed = false
    }

    /**
     * 关闭流程（成功/取消）：清流程态；**newTopicInput 不清**（关窗不丢字，2026-11-11 修订⑤
     * ——WA-1 纠偏：原清空与 MainScreen 先例相悖，命名草稿保留、创建成功才清）。
     * 多选态与选中集由宿主决定去留。
     */
    fun close() {
        step = null
        verb = null
        transferTarget = null
        executing = false
        failed = false
    }
}

/** 动作产出（消费方据此 toast／保持重试态／主列表刷新）。 */
internal data class MoveOutcome(
    /** null＝无 toast（不该出现的组合）；失败态 toast 由消费方叠加 failed 重试语义 */
    val toast: String?,
    /** true＝对话框保持变「重试」（失败态；SameDocument 等防御态 false 只提示） */
    val keepRetryState: Boolean,
    /** true＝条目经主文件增减需刷新信号（移出成功/移入成功/移入 SourceDeleteFailed）→宿主
     *  setFileLastModified＋refresh（A12：主列表经轮询/显式刷新自刷；工作区侧恒 reloadKey） */
    val refreshMain: Boolean,
    /** 仅 MOVE_IN 成功非 null：首次移入指路后缀（§15.4 U1——旗标判定归宿主，映射只拼装） */
    val guideSuffix: String? = null
)

/**
 * MoveResult 全态×动词×useSAF → toast/重试/刷新决策映射（设计 §19.2/§21.2，沿 M1 决策映射
 * 先例 JVM 钉死）。**移出/转移组合（T-26）**；移入组合随 T-28 迁入补。
 *
 * - 成功三态（moved=0／skipped>0／其余）按动词分文案：移出族 [Microcopy.toastMovedOutCount] 等
 *   （源＝主题语境句）；转移族 N>0 复用移入词族（动词归属正确，§24.2）、N=0 专用
 *   [Microcopy.toastMovedAllExistingTransfer]（禁复用主列表句——事实错误，评审 E4）；
 * - 失败族统一 [Microcopy.TOAST_MOVE_OUT_FAILED]＋重试保持；唯一变体＝移出方向目标读失败且
 *   useSAF（目标＝SAF 主文件，授权失效典型）→ SAF 冒号句式（§21.2）；
 * - [MoveResult.SameDocument]＝防御路径：guard toast、零状态变更（正常 UI 不可达——目标列表
 *   排除当前主题，事务层护栏兜底）。
 */
internal object MoveOutcomeMapping {

    fun map(
        result: MoveResult,
        verb: MoveVerb,
        useSAF: Boolean,
        sourceName: String,
        targetName: String,
        /** 仅 MOVE_IN：宿主判定「首次有效移入」后传入指路后缀（§15.4 U1）——映射只拼装不判旗标 */
        guideSuffix: String? = null
    ): MoveOutcome = when (result) {
        is MoveResult.Success -> MoveOutcome(
            toast = successToast(result, verb, sourceName, targetName) + (guideSuffix?.takeIf { verb == MoveVerb.MOVE_IN } ?: ""),
            keepRetryState = false,
            // 移入＝条目离开主文件、移出＝条目进入主文件——两者均需主列表刷新信号；转移不动主文件
            refreshMain = verb != MoveVerb.TRANSFER,
            guideSuffix = guideSuffix?.takeIf { verb == MoveVerb.MOVE_IN }
        )
        // 移入：源＝主文件（SAF 授权失效典型）；移出/转移：源＝FileBased 主题，与 SAF 无关
        MoveResult.SourceReadFailed -> fail(
            if (verb == MoveVerb.MOVE_IN && useSAF) Microcopy.TOAST_MOVE_FAILED_SAF_REVOKED
            else if (verb == MoveVerb.MOVE_IN) Microcopy.TOAST_MOVE_FAILED
            else Microcopy.TOAST_MOVE_OUT_FAILED
        )
        // 移入目标恒 FileBased 主题（无 SAF 变体）；移出目标＝主文件（useSAF→冒号变体，§21.2）
        MoveResult.TargetReadFailed -> fail(
            when {
                verb == MoveVerb.MOVE_IN -> Microcopy.toastMoveFailedTargetWrite()
                verb == MoveVerb.MOVE_OUT && useSAF -> Microcopy.TOAST_MOVE_OUT_FAILED_SAF
                else -> Microcopy.TOAST_MOVE_OUT_FAILED
            }
        )
        MoveResult.TargetWriteFailed -> fail(
            if (verb == MoveVerb.MOVE_IN) Microcopy.toastMoveFailedTargetWrite()
            else Microcopy.TOAST_MOVE_OUT_FAILED
        )
        // 移入方向特例：源＝主文件已部分移除（「两边都有」软中间态）——保持重试＋即刻如实刷新（§6.6）
        is MoveResult.SourceDeleteFailed -> fail(
            if (verb == MoveVerb.MOVE_IN) Microcopy.toastMoveFailedSourceDelete()
            else Microcopy.TOAST_MOVE_OUT_FAILED,
            refreshMain = verb == MoveVerb.MOVE_IN
        )
        MoveResult.SameDocument -> MoveOutcome(
            toast = Microcopy.toastSameTopicGuard(),
            keepRetryState = false,
            refreshMain = false
        )
    }

    /** 成功三态（moved=0／skipped>0／其余）按动词分文案（§24.1/§24.2）。 */
    private fun successToast(
        result: MoveResult.Success,
        verb: MoveVerb,
        sourceName: String,
        targetName: String
    ): String = when {
        result.moved == 0 && verb == MoveVerb.MOVE_OUT ->
            Microcopy.toastMovedOutAllExisting(result.skippedExisting)
        result.moved == 0 && verb == MoveVerb.TRANSFER ->
            Microcopy.toastMovedAllExistingTransfer(result.skippedExisting, targetName, sourceName)
        result.moved == 0 -> Microcopy.toastMovedAllExisting(result.skippedExisting)
        result.skippedExisting > 0 && verb == MoveVerb.MOVE_OUT ->
            Microcopy.toastMovedOutSkipped(result.moved, result.skippedExisting)
        result.skippedExisting > 0 ->
            Microcopy.toastMovedCountSkipped(result.moved, result.skippedExisting)
        verb == MoveVerb.MOVE_OUT -> Microcopy.toastMovedOutCount(result.moved)
        else -> Microcopy.toastMovedCount(result.moved)
    }

    private fun fail(toast: String, refreshMain: Boolean = false) =
        MoveOutcome(toast = toast, keepRetryState = true, refreshMain = refreshMain)
}
