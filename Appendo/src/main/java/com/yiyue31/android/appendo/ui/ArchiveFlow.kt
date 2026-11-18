package com.yiyue31.android.appendo.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import com.yiyue31.android.appendo.util.MarkdownFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 归档共享编排（己批次 WB-1 自 MainScreen 抽取，设计 §3）：ArchiveOutcome/Path/Action/
 * 决策映射/archiveFile 单源＋参数化 [runArchive]＋词族 [ArchiveWordbook]＋二选一框
 * [ArchiveChoiceDialog]。MainScreen 与工作区两消费方（102(g)/106）。
 */

/** 归档执行结果（修订二 93 ④：写盘段逐字节等价，仅返回值与 toast 通道变更）。 */
sealed interface ArchiveOutcome {
    data class Archived(val file: File) : ArchiveOutcome
    object NothingToArchive : ArchiveOutcome
    object Failed : ArchiveOutcome
}

/** 归档清空二选一路径（修订二 93 方案 X）。 */
enum class ArchivePath { ARCHIVE_AND_CLEAR, ARCHIVE_ONLY }

/** 二选一决策映射产物（§17.1 M1）。 */
data class ArchiveAction(
    val toast: String?,
    val shouldClear: Boolean,
)

/**
 * 归档词族（WB-1：单 lambda 表达不了四分支矩阵——成功两态按目的地分文案；
 * Failed/NothingToArchive 复用既有常量）。主界面传归档文件名；主题侧传主题名。
 */
data class ArchiveWordbook(
    val archivedAndCleared: (String) -> String,
    val archivedOnly: (String) -> String,
)

internal fun mainArchiveWordbook(): ArchiveWordbook = ArchiveWordbook(
    archivedAndCleared = { Microcopy.toastArchivedAndCleared(it) },
    archivedOnly = { Microcopy.toastArchivedTo(it) },
)

internal fun topicArchiveWordbook(topic: String): ArchiveWordbook = ArchiveWordbook(
    archivedAndCleared = { Microcopy.toastTopicArchivedAndCleared(topic) },
    archivedOnly = { Microcopy.toastTopicArchived(topic) },
)

/**
 * 归档二选一决策映射（§17.1 M1，纯函数 JVM 可测；WB-1 增 wordbook 参数缺省主界面）。
 */
internal fun archiveActionFor(
    outcome: ArchiveOutcome,
    path: ArchivePath,
    archiveName: String,
    wordbook: ArchiveWordbook = mainArchiveWordbook()
): ArchiveAction = when (outcome) {
    is ArchiveOutcome.Archived -> when (path) {
        ArchivePath.ARCHIVE_AND_CLEAR -> ArchiveAction(wordbook.archivedAndCleared(archiveName), true)
        ArchivePath.ARCHIVE_ONLY -> ArchiveAction(wordbook.archivedOnly(archiveName), false)
    }
    ArchiveOutcome.Failed -> when (path) {
        ArchivePath.ARCHIVE_AND_CLEAR -> ArchiveAction(Microcopy.TOAST_ARCHIVE_FAILED_CLEAR_KEPT, false)
        ArchivePath.ARCHIVE_ONLY -> ArchiveAction(Microcopy.TOAST_ARCHIVE_FAILED, false)
    }
    ArchiveOutcome.NothingToArchive -> ArchiveAction(Microcopy.TOAST_NOTHING_TO_ARCHIVE, false)
}

internal fun clearFailedAction(): ArchiveAction =
    ArchiveAction(Microcopy.TOAST_CLEAR_FAILED_ARCHIVED, shouldClear = false)

/**
 * 参数化归档编排（WB-1，设计 §3）：归档段＋清空段＋toast 全留共享编排；
 * [onCompleted] 仅宿主善后（主界面＝resync＋clearFileLastModified＋refresh；工作区＝reloadKey++，
 * 不触碰 fileRepository 三态与主文件 mtime 键）。[getTarget] 返 null＝同秒后缀满→Failed 不覆盖。
 */
internal fun runArchive(
    context: Context,
    scope: CoroutineScope,
    sourceMd: MarkdownFileOperations,
    getTarget: () -> File?,
    wordbook: ArchiveWordbook,
    path: ArchivePath,
    onCompleted: (shouldClear: Boolean, outcome: ArchiveOutcome) -> Unit
) {
    scope.launch {
        val outcome = withContext(Dispatchers.IO) {
            val target = getTarget()
            if (target == null) ArchiveOutcome.Failed
            else runCatching { archiveFile(context, sourceMd, target) }.getOrDefault(ArchiveOutcome.Failed)
        }
        val archiveName = (outcome as? ArchiveOutcome.Archived)?.file?.name ?: ""
        val action = archiveActionFor(outcome, path, archiveName, wordbook)

        if (outcome is ArchiveOutcome.Archived && action.shouldClear) {
            val cleared = withContext(Dispatchers.IO) {
                runCatching { sourceMd.clear() }.getOrDefault(false)
            }
            if (!cleared) {
                showToast(context, clearFailedAction().toast!!)
                onCompleted(false, outcome)
                return@launch
            }
        }
        action.toast?.let { showToast(context, it) }
        onCompleted(action.shouldClear && outcome is ArchiveOutcome.Archived, outcome)
    }
}

/**
 * Archive current content to a new file（writeAll 写盘段逐字节等价；调用方负责 IO 线程）。
 */
private fun archiveFile(
    context: Context,
    currentMdFile: MarkdownFileOperations,
    archiveFile: File
): ArchiveOutcome {
    return try {
        val currentContent = currentMdFile.readAll()
        if (currentContent.isBlank() || !currentContent.contains("## ")) {
            return ArchiveOutcome.NothingToArchive
        }
        val contentWithoutHeader = if (currentContent.startsWith(MarkdownFormatter.FILE_HEADER)) {
            currentContent.substring(MarkdownFormatter.FILE_HEADER.length)
        } else {
            currentContent
        }
        val archiveMdFile = FileBasedMarkdownFile(context, archiveFile)
        if (!archiveMdFile.writeAll(MarkdownFormatter.FILE_HEADER + contentWithoutHeader)) {
            return ArchiveOutcome.Failed
        }
        ArchiveOutcome.Archived(archiveFile)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("ArchiveFlow", "Archive failed", e)
        }
        ArchiveOutcome.Failed
    }
}

/**
 * 归档二选一共享对话框（WB-1 自 MainScreen 内联抽取——防第三份复制）。
 * [topicName] 非 null＝主题侧：正文一层明示作用域（§5.2，不叠第二层确认）。
 */
@Composable
internal fun ArchiveChoiceDialog(
    entryCount: Int,
    onArchiveAndClear: () -> Unit,
    onArchiveOnly: () -> Unit,
    onDismiss: () -> Unit,
    topicName: String? = null
) {
    val archiveAndClearFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { archiveAndClearFocus.requestFocus() } // 93：默认焦点落「归档并清空」
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Microcopy.DLG_ARCHIVE_TITLE, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    if (topicName != null) Microcopy.dlgTopicArchiveBody(entryCount, topicName)
                    else Microcopy.dlgArchiveBody(entryCount)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onArchiveAndClear,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(archiveAndClearFocus),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(Microcopy.BTN_ARCHIVE_AND_CLEAR)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onArchiveOnly,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(Microcopy.BTN_ARCHIVE_ONLY)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
