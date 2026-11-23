package com.yiyue31.android.appendo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.util.DuplicateHintThrottle

/**
 * 手动输入对话框共享组件（己批次 WA-2 自 MainScreen 抽取，设计 §2.1 契约）：无状态渲染，
 * 草稿（inputDraft）宿主持有；MainScreen 与工作区两消费方（102(d)/106）。
 *
 * 渲染面：标题「手动输入内容」；输入框 minLines 3/maxLines 6、占位符 [Microcopy.PLACEHOLDER_INPUT]
 * （常量化——原直写）、打开自动聚焦弹键盘（组件内自取键盘控制器，沿现状先例）；「添加到日历」
 * OutlinedButton（主操作位）；「保存」/「取消」。键盘收起在组件内所有关闭路径统一处理。
 *
 * @param onAppend 空校验归宿主；查重＋节流旁注**仅保存路径**（[chooseAppendToast]，§2.3——
 *   日历路径不带查重沿现状）；失败保持开框（行为修正 1）
 * @param onRequestCalendar 先追加落盘→清草稿关框→跳日历，归宿主
 */
@Composable
internal fun EntryInputDialog(
    draft: String,
    onDraftChange: (String) -> Unit,
    onAppend: () -> Unit,
    onRequestCalendar: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // 打开即聚焦输入框并弹键盘（沿手动输入对话框先例）；
        // requestFocusSafe＝N-4 恢复开态防崩（重建帧 focus 节点晚挂载竞态，2026-11-20 真机实证）
        focusRequester.requestFocusSafe()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismiss()
        },
        title = {
            Text(Microcopy.DLG_INPUT_TITLE, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text(Microcopy.PLACEHOLDER_INPUT) }
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRequestCalendar,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(Microcopy.BTN_ADD_TO_CALENDAR, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAppend) {
                Text(Microcopy.BTN_SAVE, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            // 「取消」＝仅关窗，草稿保留（与点外/返回同语义，关窗不丢字）
            TextButton(onClick = {
                keyboardController?.hide()
                onDismiss()
            }) {
                Text("取消")
            }
        }
    )
}

/**
 * 追加成功 toast 选句 helper（WA-2 消重，设计 §2.3）：非阻塞重复旁注——同内容已存在且节流窗口
 * 放行时用重复计数句式，否则普通成功句（specs 42，5s 节流防刷屏）。两宿主共用；
 * **仅输入场景保存路径消费**（日历路径不带查重旁注，沿现状）。
 */
internal fun chooseAppendToast(
    content: String,
    existingSameCount: Int,
    baseToast: String = Microcopy.TOAST_APPENDED
): String =
    if (existingSameCount > 0 && DuplicateHintThrottle.shouldShow(content)) {
        baseToast + Microcopy.duplicateSuffix(existingSameCount)
    } else {
        baseToast
    }
