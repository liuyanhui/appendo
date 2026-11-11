package com.yiyue31.android.appendo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.data.ArchiveFile
import com.yiyue31.android.appendo.data.ArchiveRepository
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.util.EntryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 归档详情条目种类（T-07 §8.5；修订二 §15.2 分家，A-5/E-5）。定义留驻本文件（零迁移）：
 * 产出源有两页——归档页（本文件，kind 恒归档、路由不带 kind 查询参数）与主题页
 * （TopicListScreen，kind=topic）；MainActivity 以 [routeValue] 构造/解析
 * `archive_detail/{filePath}?kind=` 查询参数。
 */
enum class ArchiveEntryKind(val routeValue: String) {
    ARCHIVE("archive"),
    TOPIC("topic");

    companion object {
        /** 路由查询参数解析：null / 未知值一律按归档（向后兼容旧归档路由，§8.5「归档详情行为零变化」）。 */
        fun fromRouteValue(value: String?): ArchiveEntryKind =
            if (value == TOPIC.routeValue) TOPIC else ARCHIVE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveListScreen(
    fileRepository: FileRepository,
    onNavigateBack: () -> Unit,
    onArchiveClick: (file: File) -> Unit
) {
    val context = LocalContext.current
    val archiveRepository = remember { ArchiveRepository(context) }

    var archives by remember { mutableStateOf<List<ArchiveFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var archiveToDelete by remember { mutableStateOf<ArchiveFile?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var restoreName by remember { mutableStateOf("") }
    var restoreEntryCount by remember { mutableIntStateOf(0) }

    fun loadFiles() {
        isLoading = true
        // 加载整文件归档列表（修订二 §15.2 分家：仅归档区，沿 Appendo_ 前缀判据）
        archives = archiveRepository.listArchiveFiles()
        isLoading = false
    }

    // Load files on first launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            loadFiles()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "归档管理",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    // v1.4 视觉裁定：顶栏随页面底色（colorScheme.background），与内容区无缝
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中...")
                }
            } else if (archives.isEmpty()) {
                // 归档为空 → 整页空态（修订二 §15.2 分家：归档单态，C8）
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            Microcopy.ARCHIVE_EMPTY_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Microcopy.ARCHIVE_EMPTY_BODY,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // 单 LazyColumn 归档单区（修订二 §15.2 分家：无分区标题，主题已独立成页）
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = archives,
                        key = { it.file.absolutePath }
                    ) { archive ->
                        ArchiveCard(
                            title = archive.name.removeSuffix(".md"),
                            subtitle = "${archiveRepository.formatTimestamp(archive.timestamp)} · ${archive.entryCount} 条",
                            onClick = { onArchiveClick(archive.file) },
                            onLongClick = {
                                performVibration(context, VIBRATION_DURATION_SHORT_MS)
                                // Copy all content from archive
                                try {
                                    // 出口格式化（v1.3，specs 68）：与复制/分享全部同一格式（全文复制出口）
                                    val content = EntryParser.formatForExport(
                                        EntryParser.parse(archive.file.readText())
                                    )
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("archive", content))
                                    showToast(context, Microcopy.TOAST_COPIED_ALL)
                                } catch (e: Exception) {
                                    if (BuildConfig.DEBUG) {
                                        android.util.Log.e("ArchiveListScreen", "Failed to copy archive", e)
                                    }
                                    showToast(context, "复制失败")
                                }
                            },
                            onSwipeToDelete = {
                                performVibration(context, VIBRATION_DURATION_LONG_MS)
                                archiveToDelete = archive
                                showDeleteDialog = true
                            },
                            onSwipeLeftRestore = {
                                performVibration(context, VIBRATION_DURATION_SHORT_MS)
                                restoreFile = archive.file
                                restoreName = archive.name
                                restoreEntryCount = archive.entryCount
                                showRestoreDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog（归档，迁移前原样）
    if (showDeleteDialog && archiveToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                archiveToDelete = null
            },
            title = {
                Text(
                    "删除归档",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text("确定要删除归档 \"${archiveToDelete!!.name}\" 吗？\n\n此操作无法撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = archiveRepository.deleteArchive(archiveToDelete!!.file)
                        if (deleted) {
                            showToast(context, "已删除")
                            loadFiles()
                        } else {
                            showToast(context, "删除失败")
                        }
                        showDeleteDialog = false
                        archiveToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        archiveToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // Restore confirmation dialog（归档恢复；共用件零改动消费，§8.4——主题差异行随分家移至主题页）
    if (showRestoreDialog && restoreFile != null) {
        RestoreConfirmDialog(
            file = restoreFile!!,
            sourceLabel = "归档",
            sourceName = restoreName,
            entryCount = restoreEntryCount,
            fileRepository = fileRepository,
            onDismiss = {
                showRestoreDialog = false
                restoreFile = null
                restoreEntryCount = 0
            },
            onConfirmed = {
                showRestoreDialog = false
                restoreFile = null
                restoreEntryCount = 0
                // §8.3：恢复/删除操作后重载
                loadFiles()
            }
        )
    }
}

/**
 * 归档卡片（T-13 起为薄布局包装，设计修订二 §14.1/§15.1 A8）：滑动手势骨架下沉 [SwipeCard]
 * 共用，本件仅保留内容行布局（图标＋标题/副文案）；调用点签名与手势行为零变化（行为等价红线 R16）。
 */
@Composable
private fun ArchiveCard(
    title: String,
    subtitle: String,
    swipeToDeleteEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
    onSwipeLeftRestore: () -> Unit
) {
    SwipeCard(
        swipeToDeleteEnabled = swipeToDeleteEnabled,
        onClick = onClick,
        onLongClick = onLongClick,
        onSwipeToDelete = onSwipeToDelete,
        onSwipeLeftRestore = onSwipeLeftRestore
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.size(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
