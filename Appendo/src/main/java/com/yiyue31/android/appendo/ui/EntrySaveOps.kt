package com.yiyue31.android.appendo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.yiyue31.android.appendo.data.TopicNameValidation
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.MarkdownFileOperations

/**
 * 共享写盘/出口小件（/simplify 收敛：v1.6.0 两宿主 perform* 逐行重复收拢单源）。
 * 宿主仅注入文件句柄与成功后善后（onSaved），错误处理/toast/空校验统一在此。
 */

/**
 * 编辑保存核心：空→toast 返 false；未变→返 true（不写不回调）；写成功→toast＋onSaved 返 true；
 * 写失败→toast 返 false（保持开框，修订⑤）。不清对话框状态（调用方负责）。
 */
internal fun tryEditSave(
    context: Context,
    md: MarkdownFileOperations,
    timestamp: String,
    original: String,
    draft: String,
    onSaved: () -> Unit
): Boolean {
    if (draft.isBlank()) {
        showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
        return false
    }
    if (draft == original) return true
    return try {
        if (md.updateEntry(timestamp, draft)) {
            showToast(context, Microcopy.TOAST_SAVED)
            onSaved()
            true
        } else {
            showToast(context, Microcopy.TOAST_SAVE_FAILED)
            false
        }
    } catch (e: Exception) {
        showToast(context, Microcopy.TOAST_SAVE_FAILED)
        false
    }
}

/**
 * 追加核心：空→toast 返 null；成功→toast（withDupNote 时经 chooseAppendToast 带重复旁注）＋
 * onSaved 返写入内容；失败→toast 返 null。返回内容供日历预填。
 */
internal fun tryAppend(
    context: Context,
    md: MarkdownFileOperations,
    content: String,
    baseToast: String,
    withDupNote: Boolean,
    onSaved: () -> Unit
): String? {
    if (content.isBlank()) {
        showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
        return null
    }
    return try {
        val existing = if (withDupNote) EntryParser.parse(md.readAll()).count { it.content == content } else 0
        if (md.appendReturningTimestamp(content) != null) {
            showToast(context, if (withDupNote) chooseAppendToast(content, existing, baseToast) else baseToast)
            onSaved()
            content
        } else {
            showToast(context, Microcopy.TOAST_APPEND_FAILED)
            null
        }
    } catch (e: Exception) {
        showToast(context, Microcopy.TOAST_APPEND_FAILED)
        null
    }
}

/** 单条复制出口（/simplify 收敛 5 处三段式样板）：剪贴板＋已复制 toast；振动由调用方按需。 */
internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("entry", text))
    showToast(context, Microcopy.TOAST_COPIED)
}

/** 主题命名校验→toast 单源映射（/simplify 收敛创建/改名三处独立维护）。 */
internal fun validationToast(validation: TopicNameValidation): String = when (validation) {
    TopicNameValidation.Empty -> Microcopy.TOAST_TOPIC_NAME_EMPTY
    TopicNameValidation.Reserved -> Microcopy.TOAST_TOPIC_NAME_RESERVED
    TopicNameValidation.IllegalChars -> Microcopy.TOAST_TOPIC_NAME_ILLEGAL
    TopicNameValidation.TooLong -> Microcopy.TOAST_TOPIC_NAME_TOO_LONG
    is TopicNameValidation.Exists -> Microcopy.dlgTopicPageSameNameBody(validation.displayName)
    is TopicNameValidation.Ok -> ""
}
