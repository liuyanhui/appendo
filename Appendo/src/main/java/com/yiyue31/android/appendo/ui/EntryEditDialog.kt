package com.yiyue31.android.appendo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 条目编辑对话框共享组件（己批次 WA-2 自 MainScreen 抽取，设计 §2.1 契约）：**无状态渲染**——
 * 草稿（editDraft＋lastEditedTs 归属标记）由宿主持有并镜像（§2.2 三件套纪律），本组件只渲染
 * 传入值＋转发回调；MainScreen 与工作区两消费方（102(a)/106）。
 *
 * 渲染面（抽取时逐项钉死）：标题「编辑条目」；时间戳行（labelSmall/primary，派生自 [entry]）；
 * 输入框 minLines 5/maxLines 10、占位符 [Microcopy.PLACEHOLDER_EDIT]、内容区 verticalScroll；
 * 「添加到日历」OutlinedButton（主操作位——16.C 提醒承接，75 先落盘时序归宿主回调）；
 * 「保存」/「取消」。空校验、保存/日历时序、关窗清草稿均归宿主（§2.3）。
 *
 * @param onSave 空校验与「内容未变直接关框」判断归宿主；失败保持开框（行为修正 1，修订⑤）
 * @param onRequestCalendar 空校验→有修改先保存（失败 return 保持开框）→清草稿关框跳日历，归宿主
 * @param onDismiss 点外/「取消」/系统返回三通道同路——仅关窗不清草稿（修订⑤；键盘收起组件内处理）
 */
@Composable
internal fun EntryEditDialog(
    entry: LinkEntry,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onRequestCalendar: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(Microcopy.DLG_EDIT_TITLE, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = entry.timestampDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                    placeholder = { Text(Microcopy.PLACEHOLDER_EDIT) }
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
            TextButton(onClick = onSave) {
                Text(Microcopy.BTN_SAVE, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            // 「取消」＝仅关窗，编辑中内容保留（关窗不丢字，与点外/返回同语义）
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
