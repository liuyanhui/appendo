package com.yiyue31.android.appendo.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import java.io.File

/**
 * 归档 / 主题恢复共用件（topic-move 设计 §8.4，T-07）：确认对话框＋逐条 [com.yiyue31.android.appendo.util.MarkdownFileOperations.appendEntry]
 * 去重追加循环，自 ArchiveListScreen（迁移前 :284-371）**原样迁移**——逻辑逐行等价，仅三处必要抽参：
 *
 * 1. 源经参数注入：[file] / [sourceName]（归档＝文件名含 .md，迁移前渲染原样；主题＝displayName 不含 .md）
 *    ＋正文名词 [sourceLabel]（「归档」/「主题」，归档路径渲染与迁移前逐字节一致）；
 * 2. [restoreNote]：主题路径正文末追加注明行（86 修订，§8.4 差异 1——[Microcopy.TOPIC_RESTORE_NOTE]）；
 *    归档传 null，正文零变化。toast 两路径同字面复用第 15 章常量（§8.4 差异 2——迁移前已是）；
 * 3. 对话框状态清理与恢复后重载（§8.3「恢复/删除操作后重载」）经 [onDismiss] / [onConfirmed] 回调
 *    交调用方（迁移前在 onClick 尾部无条件清理三态，[onConfirmed] 同语义：try/catch 后必触发）。
 *
 * 等价性判据：`ArchiveRestoreDeduplicationTest` 零修改全绿（去重 key 算法 `timestamp|content` 同式）
 * ＋T-10 真机抽查（§10.4「归档恢复迁移等价」）。日志 tag 沿用迁移前 "ArchiveListScreen"（保持
 * T-10 日志事实源 grep 连续性）。
 */
@Composable
internal fun RestoreConfirmDialog(
    /** 待恢复源文件（归档 / 主题文档同构：FILE_HEADER＋条目模板） */
    file: File,
    /** 正文名词：「归档」/「主题」 */
    sourceLabel: String,
    /** 正文展示名：归档＝文件名（含 .md，迁移前原样）；主题＝displayName（不含 .md） */
    sourceName: String,
    /** 恢复确认正文中的条数（滑动手势触发时点的源计数快照，迁移前同） */
    entryCount: Int,
    fileRepository: FileRepository,
    /** 主题路径注明行（追加在正文末）；归档传 null */
    restoreNote: String? = null,
    /** 取消 / 点外关闭：调用方清恢复对话框状态 */
    onDismiss: () -> Unit,
    /** 确认执行完成（成败均触发，迁移前 onClick 尾部无条件清理同语义）：调用方清状态＋重载列表 */
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "追加到当前文档",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            val body =
                "确定要将$sourceLabel \"$sourceName\" 中的 $entryCount 条内容追加到当前文档吗？" +
                restoreNote?.let { "\n$it" }.orEmpty()
            Text(body)
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val archiveContent = file.readText()
                        val archiveEntries = parseMarkdownEntries(archiveContent)

                        val mdFile = MarkdownFileFactory.create(
                            context,
                            fileRepository.isUsingSAF(),
                            fileRepository.getFileUri(),
                            fileRepository.getDefaultFile()
                        )

                        // 去重（timestamp|content），用 appendEntry 保留归档原时间戳（v1.1 B3：去重 key 才真正生效）
                        val existingContent = mdFile.readAll()
                        val existingEntries = parseMarkdownEntries(existingContent)
                        val existingKeys = existingEntries
                            .map { "${it.timestamp}|${it.content}" }
                            .toMutableSet()

                        var addedCount = 0
                        var skippedCount = 0
                        for (entry in archiveEntries) {
                            val key = "${entry.timestamp}|${entry.content}"
                            if (key !in existingKeys) {
                                mdFile.appendEntry(entry.timestamp, entry.content)
                                existingKeys.add(key)
                                addedCount++
                            } else {
                                skippedCount++
                            }
                        }

                        if (addedCount > 0) {
                            fileRepository.setFileLastModified(System.currentTimeMillis())
                        }
                        // 透明提示（specs 43）：Y=0 不显示跳过后缀
                        val msg = when {
                            addedCount == 0 && skippedCount == 0 -> "归档为空，无需追加"
                            skippedCount == 0 -> Microcopy.toastAppendedCount(addedCount)
                            else -> Microcopy.toastAppendedCountSkipped(addedCount, skippedCount)
                        }
                        showToast(context, msg)
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.e("ArchiveListScreen", "Failed to restore archive", e)
                        }
                        showToast(context, Microcopy.TOAST_APPEND_FAILED)
                    }
                    onConfirmed()
                }
            ) {
                Text(Microcopy.BTN_APPEND, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("取消")
            }
        }
    )
}
