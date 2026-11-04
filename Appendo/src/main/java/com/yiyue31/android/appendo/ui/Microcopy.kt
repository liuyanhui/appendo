package com.yiyue31.android.appendo.ui

/**
 * 微文案常量源（v1.3.x S1，specs 第 15 章 / design「v1.3.x 微文案与交互一致性设计」§3）。
 *
 * 集中「新增条目、修改条目、归档恢复」三类写盘动作的按钮与反馈文案：按钮与 Toast
 * 引用同一常量，编译期保证"按钮/成功/失败动词一致"（specs 74）。含参文案用函数返回。
 *
 * 范围控制：仅第 15 章涉及文案及其直接共用项（含两处达标同族文案"已复制全部内容"
 * "已追加（已有相同内容 N 条）"），不做全量 ~50 条收敛（超出本批范围）。
 * 选型理由：单语言中文、无 localized 需求，Kotlin 常量 object 成本低于 strings.xml
 * 且编译期引用（设计 §3）。
 */
internal object Microcopy {

    // ---- 确认按钮（specs 74 术语表 / 77 动词化）----

    /** 归档恢复的确认按钮（2026-10-08 修订：手动输入确认按钮改用 BTN_SAVE）。 */
    const val BTN_APPEND = "追加"

    /** 手动输入与编辑两对话框的确认按钮（2026-10-08 修订：字面统一「保存」）。 */
    const val BTN_SAVE = "保存"

    // ---- 新增条目 / 归档恢复反馈（specs 74）----

    /** 追加成功（无旁注；手动输入两次级动作路径共用，不带重复旁注）。 */
    const val TOAST_APPENDED = "已追加"

    /** 追加失败（手动输入 / 分享接收 / 归档恢复全路径共用）。 */
    const val TOAST_APPEND_FAILED = "追加失败"

    /** 追加成功 + 重复旁注（需求 42 语义不变；仅手动输入确认按钮路径使用）。 */
    fun toastAppendedWithDuplicates(count: Int): String = "已追加（已有相同内容 $count 条）"

    /** 归档恢复成功（无跳过，specs 43：Y=0 不显示跳过后缀）。 */
    fun toastAppendedCount(added: Int): String = "已追加 $added 条"

    /** 归档恢复成功（有跳过）。 */
    fun toastAppendedCountSkipped(added: Int, skipped: Int): String = "已追加 $added 条（跳过 $skipped 条已存在）"

    // ---- 修改既有条目反馈（specs 74）----

    const val TOAST_SAVED = "已保存"

    const val TOAST_SAVE_FAILED = "保存失败"

    // ---- 空内容校验（specs 76a：手动输入/编辑两对话框五处共用一个常量）----

    const val TOAST_CONTENT_EMPTY = "内容不能为空"

    // ---- 复制反馈（specs 79）----

    /** 复制全部 / 归档长按复制全文（两处同字面，入常量保证一致）。 */
    const val TOAST_COPIED_ALL = "已复制全部内容"

    // ---- 编辑态占位（specs 79：「无内容」→「请输入内容」）----

    const val PLACEHOLDER_EDIT = "请输入内容"

    // ---- 分享接收路径（specs 74/76b；成功回执「Appendo已收到」保留原状）----

    /** 分享超长：拒绝写入（不截断）。 */
    fun toastTooLong(limit: Int): String = "内容过长（上限 $limit 字符），未追加"

    /** 分享无有效内容（76b 拒绝写入）。 */
    const val TOAST_NOTHING_TO_APPEND = "未找到可追加的内容"

    /** SAF 授权失效自动回退默认文件（TD-021：明示回退，防数据"无声分家"）。 */
    const val TOAST_APPENDED_TO_DEFAULT = "已追加到默认文件（自定义目录已失效，可打开 appendo 重选）"

    /** SAF 写失败（授权可能已失效，附注保留）。 */
    const val TOAST_APPEND_FAILED_SAF_REVOKED = "追加失败：自定义目录授权可能已失效，请打开 appendo 重选文件"

    // ---- 次级动作后续反馈（specs 75：独立反馈，不回滚已落盘内容）----

    const val TOAST_NO_CALENDAR = "未找到日历应用"

    // ---- 「移入主题」多选（topic-move 设计 §9.1/§9.2；T-05 增量落盘，T-08 全套核对收口）----

    /** 多选顶栏「移入」按钮（§9.2 多选顶栏行）；与 §9.1 对话框 3 确认按钮同字面同常量（T-06 复用）。 */
    const val BTN_MOVE_IN = "移入"

    /** 多选态顶栏标题「已选 N 条」（§9.2，N = selectedKeys.size 实时）。 */
    fun topBarSelectedCount(count: Int): String = "已选 $count 条"

    /** 预览对话框复制反馈（§9.2 预览行；需求 79「已复制」句式，浏览态长按复制同字面共用）。 */
    const val TOAST_COPIED = "已复制"

    // ---- 「移入主题」移入词族（topic-move 设计 §9.1；T-06 增量落盘，T-08 全套核对收口）----
    // 数字口径（§9.1 句式对齐说明，88「全部有着落」）：
    // N＝所选条数＝moved＋skippedExisting（＋missingFromSource，恒 0 不显示——keys 取自当前源
    //    解析，所选必在源；成功文案 N 取实际 moved 实写数）；
    // M＝目标已存在跳过条数（skippedExisting，去重 key＝timestamp|content 与归档恢复同源）；
    // K＝提醒取消数（独立附注，非条目计数；实际取消按 ts 计——一条提醒覆盖同 ts 全部条目，
    //    附注数字为同 ts 连坐防护（§8.2 ③）过滤后的实际取消数）。

    /** 执行中按钮（对话框 3；89(p) 可感知进行反馈，事务在 Dispatchers.IO 不 ANR）。 */
    const val BTN_MOVING = "移入中…"

    /** 失败重试按钮（对话框 3 失败态：对话框保持，重试幂等收敛，84(b)）。 */
    const val BTN_RETRY = "重试"

    /** 成功（M=0）：「已移入 N 条」——N＝moved 实写数（所选全部写入目标，无跳过）。 */
    fun toastMovedCount(n: Int): String = "已移入 $n 条"

    /** 成功（M>0）：N＝moved 实写数、M＝目标已存在跳过（skippedExisting）；对齐第 15 章归档恢复「（跳过 M 条已存在）」句式族（O2 裁定）。 */
    fun toastMovedCountSkipped(n: Int, m: Int): String = "已移入 $n 条（跳过 $m 条已存在）"

    /** 成功（N=0，87 修订）：M＝全部所选（moved=0、全 skipped）——直陈事实句式，禁「已移入 0 条」失败读感。 */
    fun toastMovedAllExisting(m: Int): String = "$m 条已在该主题中，已从当前列表移除"

    /** 成功附注（K>0，84(c)）：与主文案直接拼接；K＝实际取消数、按 ts 计（§8.2 ⑤——同 ts 连坐防护 ③ 过滤后，一条提醒覆盖同 ts 全部条目）。 */
    fun removedRemindersSuffix(k: Int): String = "，已取消 $k 条提醒"

    /** 失败（统一兜底；非 SAF 源读失败等无专属句式场景）。 */
    const val TOAST_MOVE_FAILED = "移入失败"

    /** 失败·源读（SAF 授权失效）：对齐 TOAST_APPEND_FAILED_SAF_REVOKED 冒号句式（动词失败：原因，行动指引）。 */
    const val TOAST_MOVE_FAILED_SAF_REVOKED = "移入失败：自定义目录授权可能已失效，请打开 appendo 重选文件"

    /** 失败·写入段（84(b)：目标可能已部分写入，可整体重试）。 */
    fun toastMoveFailedTargetWrite(): String = "移入失败（写入主题文档失败）"

    /** 失败·删除段（84(b)：明示「重试安全」——内容已在目标，重试只完成移除）。 */
    fun toastMoveFailedSourceDelete(): String = "移入失败（主题已保存，从当前列表移除未完成，请重试）"

    // ---- 「移入主题」流程与校验（topic-move 设计 §9.2；T-06 增量落盘，T-08 收口）----

    /** 对话框 1/3 标题。 */
    const val DLG_MOVE_TITLE = "移入主题"

    /** 确认正文（84(d) 后果去向两行；修订二 §16.2：第二行去向改顶栏入口，specs 92 逐字）；N＝所选条数（selectedKeys.size，打开对话框时快照）。 */
    fun dlgMoveConfirmBody(n: Int, topic: String): String =
        "将把 $n 条移入主题「$topic」。\n移入后这些条目将从主列表消失，可在顶栏『主题』入口查看。"

    /** 确认正文提醒行（K>0 追加一行）；K＝预览值、按条计（所选中 ts 设有未触发提醒的条数；同 ts 多条重复计），实际取消按 ts 计、成功回调内重算（§8.2 ②）——预览 K ≥ 实际 K 属 R8 同根边界。 */
    fun dlgMoveConfirmReminder(k: Int): String = "其中 $k 条设有未触发提醒，移入后将取消。"

    /** 对话框 1 列表末项。 */
    const val ITEM_NEW_TOPIC = "新建主题…"

    /** 对话框 2 标题 / 输入占位。 */
    const val DLG_NEW_TOPIC_TITLE = "新建主题"
    const val PLACEHOLDER_TOPIC_NAME = "请输入主题名称"

    /** 对话框 2 确认按钮。 */
    const val BTN_CREATE_TOPIC = "创建"

    /** 命名·空（对齐「内容不能为空」句式）。 */
    const val TOAST_TOPIC_NAME_EMPTY = "名称不能为空"

    /** 命名·保留字。 */
    const val TOAST_TOPIC_NAME_RESERVED = "不能用“Appendo”命名，也不能以“Appendo_”开头"

    /** 命名·非法字符。 */
    const val TOAST_TOPIC_NAME_ILLEGAL = "名称不能包含 / \\ : * ? “ < > | 等字符"

    /** 命名·超长。 */
    const val TOAST_TOPIC_NAME_TOO_LONG = "名称过长（上限 50 字符）"

    /** 同名指路（83 修订 c）：标题 / 正文 / 「移入该主题」按钮。 */
    const val DLG_SAME_NAME_TITLE = "已存在同名主题"
    fun dlgSameNameBody(x: String): String = "已存在同名主题“$x”，是否移入该主题？"
    const val BTN_MOVE_TO_EXISTING = "移入该主题"

    /** 创建文档失败（新建路径，§8.1：toast 后流程中止）。 */
    fun toastMoveFailedCreateTopic(): String = "移入失败（创建主题文档失败）"

    // ---- 「移入主题」分区列表（topic-move 设计 §8.3/§9.2；T-07 增量落盘，T-08 全套核对收口）----

    /** 分区标题（§9.2）：主题分区在前（高频取用）、归档分区在后。 */
    const val SECTION_TOPICS = "主题文档"
    const val SECTION_ARCHIVES = "归档"

    /** 整页空态两行（§8.3 结构行：两区皆空）。 */
    const val ARCHIVE_TOPIC_EMPTY_TITLE = "暂无归档与主题"
    const val ARCHIVE_TOPIC_EMPTY_BODY = "归档与主题文件会显示在这里"

    /** 空主题删除（§9.2）：标题（error 红，78(a) 破坏性=红）/ 正文；按钮「删除」（红）/「取消」沿既有字面。 */
    const val DLG_TOPIC_DELETE_TITLE = "删除空主题"
    fun dlgTopicDeleteBody(x: String): String = "确定删除空主题“$x”吗？此操作无法撤销。"

    /** 空主题删除失败 toast（NotEmpty 兜底同文案）。 */
    const val TOAST_TOPIC_DELETE_FAILED = "删除失败"

    /** 主题恢复注明行（86 修订，§9.2：追加在恢复确认正文末——恢复＝复制语义）。 */
    const val TOPIC_RESTORE_NOTE = "追加后主题文档中仍保留这些条目。"

    /** 主题卡片副文案（修订二 §16.2：「N 条 · 创建时间」**条数在前**，90；[timeLabel] 为调用方格式化串，
     *  弱化小字 onSurfaceVariant；唯一消费方＝主题页 TopicCard）。 */
    fun topicCardSubtitle(count: Int, timeLabel: String): String = "$count 条 · $timeLabel"

    /** 空主题副文案（§8.3：可发现性——右滑删除手势不可见，副文案「空」补位提示可删状态）。 */
    const val TOPIC_EMPTY_SUBTITLE = "空"

    // ---- 主题独立页（topic-move 设计修订二 §16.1；T-14 增量落盘，T-16 全套核对收口）----

    /** 页级空态两行（§15.1：无任何主题时的引导文案，90）。 */
    const val TOPIC_PAGE_EMPTY_TITLE = "暂无主题"
    const val TOPIC_PAGE_EMPTY_BODY = "点击右上角“＋”新建主题，或在主界面多选条目后「移入主题」"

    /** 页内新建·同名提示正文（§15.1 C5：提示非指路——主题页语境无移入动作，无「移入该主题」按钮）。 */
    fun dlgTopicPageSameNameBody(x: String): String = "已存在同名主题“$x”，请换一个名称。"

    /** 页内新建·同名提示确认按钮（C5：回命名框，输入保留可改名）。 */
    const val BTN_GOT_IT = "知道了"

    /** 页内新建成功（§15.1 新建流程行：关框＋重载）。 */
    const val TOAST_TOPIC_CREATED = "已创建"

    /** 页内新建·创建文档失败（框保持、输入保留可重试）。 */
    const val TOAST_TOPIC_CREATE_FAILED = "创建失败"

    // ---- 归档清空合并（topic-move 设计修订二 §16.1；T-15 增量落盘，T-16 全套核对收口）----

    /** 网格第四格按钮（§15.3：与「复制全部/分享全部」同款形态，点击触发；旧长按清空退役）。 */
    const val BTN_CELL_ARCHIVE = "归档"

    /** 二选一对话框标题（§15.3 C6：默认色——路径选择非破坏）。 */
    const val DLG_ARCHIVE_TITLE = "归档"

    /** 二选一对话框正文（§15.3：N＝entryCount 实时值，88 数字透明口径）。 */
    fun dlgArchiveBody(n: Int): String = "将把已收集的 $n 条全部备份为归档文件。"

    /** 路径一「归档并清空」（78(a) 破坏性警示下沉至此按钮=error 红填充；默认焦点，93）。 */
    const val BTN_ARCHIVE_AND_CLEAR = "归档并清空"

    /** 路径二「只归档，保留列表」（78(b) 备份非破坏=primary；需求 12 快照语义）。 */
    const val BTN_ARCHIVE_ONLY = "只归档，保留列表"

    /** 只归档成功（§16.1：现状字面去「: 」常量化）。 */
    fun toastArchivedTo(name: String): String = "已归档到 $name"

    /** 归档并清空成功（C6 双 toast 归并：备份去向＋清空结果一句说完，信息零丢失）。 */
    fun toastArchivedAndCleared(name: String): String = "已归档到 $name，已清空"

    /** 只归档失败。 */
    const val TOAST_ARCHIVE_FAILED = "归档失败"

    /** 归档并清空·归档失败（TD-017：失败不得清空，94(d) 断言）。 */
    const val TOAST_ARCHIVE_FAILED_CLEAR_KEPT = "归档失败，未清空"

    /** 归档并清空·清空段失败（明示备份安全）。 */
    const val TOAST_CLEAR_FAILED_ARCHIVED = "清空失败（归档已保存）"

    /** 触发守卫（§15.3：主列表 0 条点击 → toast 不开框；现状字面常量化）。 */
    const val TOAST_NOTHING_TO_ARCHIVE = "暂无内容可归档"

    /** 首次指路后缀（§15.4 U1：拼接序末位——基础→提醒附注→本后缀，单 toast 原则）。 */
    const val firstMoveGuideSuffix = "，可在顶栏『主题』查看"
}
