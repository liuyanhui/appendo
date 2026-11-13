package com.yiyue31.android.appendo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.data.ArchiveRepository
import com.yiyue31.android.appendo.data.TopicDocument
import java.util.Date

/**
 * 「移入主题」流程对话框组（topic-move 设计 §8.1/§7.4，T-06）：
 * - [MoveFlowStep] ＋ MainScreen 侧状态机驱动：对话框 1 目标选择 → 2 新建命名 ⇄ 2b 同名指路
 *   → 3 确认（含执行中/失败重试态）；取消路径逐对话框定义（见各组件 KDoc）；
 * - [EntryPreviewDialog]：多选态长按预览（82 修订 b，§7.4）。
 * - 提醒联动取消退役（95(e)，T-23）：确认框 K 行改保留告知（[Microcopy.reminderKeepLine]）；
 *   旧 [~~remindersToCancel~~] 纯函数与「提醒失效」口径一并退役（§20.3/§20.5）。
 *
 * 颜色全部经 MaterialTheme.colorScheme（P6/P7/P8）：确认/创建/移入该主题按钮＝primary、
 * 辅助小字＝onSurfaceVariant、零写死色；用户可见字面全部经 [Microcopy]（§9 定稿）。
 */

/**
 * 「移入主题」对话框栈步骤（§8.1 流程图的代码化）：
 * SELECT_TARGET（对话框 1）→ NEW_TOPIC（对话框 2）⇄ SAME_NAME（对话框 2b）→ CONFIRM（对话框 3）；
 * null＝流程未开启。取消路径：3 与 1 关全部（保持多选态与选中集）；2 回 1；2b 回 2（输入保留）。
 */
internal enum class MoveFlowStep { SELECT_TARGET, NEW_TOPIC, SAME_NAME, CONFIRM }

/**
 * 「移入主题」对话框栈步骤（§8.1 流程图的代码化）：
 * 对话框 1 目标选择（§8.1）：主题列表 createdAt 倒序（[TopicRepository.listTopics] 序），
 * 名称主文字＋「yyyy-MM-dd HH:mm · N 条」弱化小字（P3/P8，onSurfaceVariant——格式与归档列表
 * [ArchiveRepository.formatTimestamp] 同源）；末项「新建主题…」（primary，P6 组件级用途）。
 * 点击已有主题 → [onSelect]（目标＝该主题，进对话框 3）；末项 → [onNewTopic]（进对话框 2）；
 * 点外/返回 → [onDismiss]（关全部，保持多选态与选中集）。
 */
@Composable
internal fun TopicSelectDialog(
    topics: List<TopicDocument>,
    onSelect: (TopicDocument) -> Unit,
    onNewTopic: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val archiveRepository = remember { ArchiveRepository(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Microcopy.DLG_MOVE_TITLE, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                topics.forEach { topic ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(topic) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(topic.displayName, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${topic.entryCount} 条 · ${archiveRepository.formatTimestamp(Date(topic.createdAt))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 列表末项「新建主题…」（§8.1）：动作项 → 对话框 2
                Text(
                    Microcopy.ITEM_NEW_TOPIC,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNewTopic() }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {}
    )
}

/**
 * 对话框 2 新建命名（§8.1）：OutlinedTextField 自动聚焦弹键盘（沿 MainScreen 手动输入对话框惯例）。
 * 确认「创建」→ [onCreate]（校验链与创建一体经 TopicRepository.createEmptyTopic，原子无间隙；
 * 各校验失败分支→toast 且对话框保持，同名→对话框 2b，创建失败→中止流程，§8.1）。
 * 「取消」/点外 → [onDismiss]（回对话框 1）。输入状态由调用方持有——对话框 2b「取消」
 * 返回本对话框时输入保留（§8.1）。
 */
@Composable
internal fun NewTopicNameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // 打开即聚焦输入框并弹键盘（沿手动输入对话框先例）
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismiss()
        },
        title = { Text(Microcopy.DLG_NEW_TOPIC_TITLE, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text(Microcopy.PLACEHOLDER_TOPIC_NAME) }
            )
        },
        confirmButton = {
            TextButton(onClick = onCreate) {
                Text(Microcopy.BTN_CREATE_TOPIC, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
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
 * 对话框 2b 同名指路（§8.1，83 修订 c）：「移入该主题」→ [onMoveToExisting]（目标切该已有主题，
 * 进对话框 3）；「取消」/点外 → [onDismiss]（回对话框 2，输入保留——输入状态在调用方未动）。
 */
@Composable
internal fun SameNameDialog(
    existingName: String,
    onMoveToExisting: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Microcopy.DLG_SAME_NAME_TITLE, fontWeight = FontWeight.SemiBold) },
        text = { Text(Microcopy.dlgSameNameBody(existingName)) },
        confirmButton = {
            TextButton(onClick = onMoveToExisting) {
                Text(Microcopy.BTN_MOVE_TO_EXISTING, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 对话框 3 确认（§8.1，84(d)）：正文＝后果去向两行＋K>0 提醒行（K 为预览值，§8.2 ②在成功
 * 回调内重算）；辅助小字 onSurfaceVariant（P8）。
 * 确认按钮三态：[executing]＝禁用＋「移入中…」（89(p) 可感知进行反馈）；[failed]＝「重试」
 * （对话框保持、保持勾选与目标，一键重试幂等收敛，84(b)）；其余＝「移入」（primary）。
 * 执行中「取消」与点外关闭一并禁用（事务在途，防中途关框竞态）。
 * 「取消」→ [onDismiss]（关全部对话框，**保持多选态与选中集**，§8.1）。
 */
@Composable
internal fun MoveConfirmDialog(
    count: Int,
    topicName: String,
    reminderCount: Int,
    executing: Boolean,
    failed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!executing) onDismiss() },
        title = { Text(Microcopy.DLG_MOVE_TITLE, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(Microcopy.dlgMoveConfirmBody(count, topicName))
                if (reminderCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        Microcopy.reminderKeepLine(reminderCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !executing) {
                Text(
                    when {
                        executing -> Microcopy.BTN_MOVING
                        failed -> Microcopy.BTN_RETRY
                        else -> Microcopy.BTN_MOVE_IN
                    },
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!executing) onDismiss() },
                enabled = !executing
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 多选态长按预览对话框（§7.4，82 修订 b）：复刻编辑对话框同构布局（标题＋正文滚动＋底部双按钮，
 * 不共享实现只对齐视觉结构）。标题＝条目时间戳（§9.2），按小字弱化（onSurfaceVariant——
 * 时间戳是元数据、内容才是主层级，P3/P8）；全文 verticalScroll；「复制」（primary）复制该条内容
 * ——[LinkEntry.content] 为 parse 产物语义（ZWSP 已剥离），出口天然干净（89(h) 零宽判据），
 * 复制后对话框保持、toast 复用 [Microcopy.TOAST_COPIED]（需求 79 句式）；「关闭」关闭。
 */
@Composable
internal fun EntryPreviewDialog(entry: LinkEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                entry.timestampDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(entry.content, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 与浏览态长按复制同款：复制 parse 产物内容（无 ZWSP），对话框保持
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("entry", entry.content))
                    showToast(context, Microcopy.TOAST_COPIED)
                }
            ) {
                Text("复制", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
