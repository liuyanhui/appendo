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
 * **两步走过渡态（§25.4 复核三方收敛）**：L1 期本 holder 供工作区屏单消费——MainScreen
 * 暂持旧 executeMove 编排（移入），L2 T-28 抽取提交时统一迁入（Mapping 补「移入」组合）。
 *
 * 执行编排纪律（沿 MainScreen.executeMove 先例）：事务在 Dispatchers.IO；成功→统一失效
 * 规则 invalidate(源＋目标)（§20.2）＋本页重载；失败→对话框保持变「重试」、保持勾选与目标
 * （84(b) 幂等收敛）；SameDocument＝防御路径只提示不动上下文（96）。
 */

/** 流程动词（T-26：移出/转移；移入随 T-28 迁入补）。 */
internal enum class MoveVerb { MOVE_OUT, TRANSFER }

/**
 * 流程状态 holder（对话框栈驱动见 [MoveFlowStep]）：
 * [step] null＝未开启；[verb] 记录发起动词（确认句/产出映射分场景）；[transferTarget]＝
 * 转移目标主题（移出方向恒 null——目标＝主文件无选择对话框）；新建子流程输入 [newTopicInput]
 * 由本 holder 持有（D2：创建成功即为目标；取消路径输入保留——草稿保全先例）。
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

    /** 关闭流程（成功/取消）：清全部状态与新建草稿；多选态与选中集由宿主决定去留。 */
    fun close() {
        step = null
        verb = null
        transferTarget = null
        newTopicInput = ""
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
    /** true＝成功且条目进了主文件（移出方向）→宿主 setFileLastModified（评审 A12：主列表经轮询自刷） */
    val refreshMain: Boolean
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
        targetName: String
    ): MoveOutcome = when (result) {
        is MoveResult.Success -> MoveOutcome(
            toast = when {
                result.moved == 0 && verb == MoveVerb.MOVE_OUT ->
                    Microcopy.toastMovedOutAllExisting(result.skippedExisting)
                result.moved == 0 -> Microcopy.toastMovedAllExistingTransfer(
                    result.skippedExisting, targetName, sourceName
                )
                result.skippedExisting > 0 && verb == MoveVerb.MOVE_OUT ->
                    Microcopy.toastMovedOutSkipped(result.moved, result.skippedExisting)
                result.skippedExisting > 0 ->
                    Microcopy.toastMovedCountSkipped(result.moved, result.skippedExisting)
                verb == MoveVerb.MOVE_OUT -> Microcopy.toastMovedOutCount(result.moved)
                else -> Microcopy.toastMovedCount(result.moved)
            },
            keepRetryState = false,
            refreshMain = verb == MoveVerb.MOVE_OUT
        )
        MoveResult.SourceReadFailed -> fail(Microcopy.TOAST_MOVE_OUT_FAILED)
        MoveResult.TargetReadFailed -> fail(
            if (verb == MoveVerb.MOVE_OUT && useSAF) Microcopy.TOAST_MOVE_OUT_FAILED_SAF
            else Microcopy.TOAST_MOVE_OUT_FAILED
        )
        MoveResult.TargetWriteFailed -> fail(Microcopy.TOAST_MOVE_OUT_FAILED)
        is MoveResult.SourceDeleteFailed -> fail(Microcopy.TOAST_MOVE_OUT_FAILED)
        MoveResult.SameDocument -> MoveOutcome(
            toast = Microcopy.toastSameTopicGuard(),
            keepRetryState = false,
            refreshMain = false
        )
    }

    private fun fail(toast: String) = MoveOutcome(toast = toast, keepRetryState = true, refreshMain = false)
}
