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

    /** 新增条目 / 归档恢复的确认按钮（手动输入、归档恢复均用「追加」）。 */
    const val BTN_APPEND = "追加"

    /** 修改既有条目的确认按钮（编辑对话框）。 */
    const val BTN_SAVE_CHANGES = "保存修改"

    /** 清空确认按钮（需求 77「确定」→「清空」；与主界面清空按钮同字面共用）。 */
    const val BTN_CLEAR = "清空"

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
}
