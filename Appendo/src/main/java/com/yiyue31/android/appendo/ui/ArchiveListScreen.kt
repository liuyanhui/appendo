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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.yiyue31.android.appendo.data.TopicDeleteResult
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.util.EntryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * 「归档与主题」列表条目种类（T-07，§8.3/§8.5）。定义在本文件（分区列表页为唯一产出源）；
 * MainActivity 以 [routeValue] 构造/解析 `archive_detail/{filePath}?kind=` 查询参数。
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
    onFileClick: (file: File, kind: ArchiveEntryKind) -> Unit
) {
    val context = LocalContext.current
    val archiveRepository = remember { ArchiveRepository(context) }
    val topicRepository = remember { TopicRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var archives by remember { mutableStateOf<List<ArchiveFile>>(emptyList()) }
    var topics by remember { mutableStateOf<List<TopicDocument>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var archiveToDelete by remember { mutableStateOf<ArchiveFile?>(null) }
    var archiveToDeleteIndex by remember { mutableIntStateOf(-1) }
    var showTopicDeleteDialog by remember { mutableStateOf(false) }
    var topicToDelete by remember { mutableStateOf<TopicDocument?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var restoreName by remember { mutableStateOf("") }
    var restoreKind by remember { mutableStateOf(ArchiveEntryKind.ARCHIVE) }
    var restoreEntryCount by remember { mutableIntStateOf(0) }

    fun loadFiles() {
        isLoading = true
        // topics + archives 合并加载（§8.3，对齐迁移前 loadArchives 形态）
        archives = archiveRepository.listArchiveFiles()
        topics = topicRepository.listTopics()
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
                        "归档与主题",
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
                    containerColor = MaterialTheme.colorScheme.surface
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
            } else if (archives.isEmpty() && topics.isEmpty()) {
                // 两区皆空 → 整页空态（§8.3）
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            Microcopy.ARCHIVE_TOPIC_EMPTY_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Microcopy.ARCHIVE_TOPIC_EMPTY_BODY,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // 单 LazyColumn 两分区：主题文档在前（高频取用）、归档在后；空区不渲染含标题（§8.3）
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (topics.isNotEmpty()) {
                        item(key = "section_topics") {
                            SectionHeader(Microcopy.SECTION_TOPICS)
                        }
                        items(
                            items = topics,
                            key = { "topic_" + it.file.absolutePath }
                        ) { topic ->
                            val index = topics.indexOf(topic)
                            ArchiveCard(
                                title = topic.displayName,
                                // 空主题副文案「空」（§8.3 可发现性：滑动手势不可见，副文案补位提示可删状态）
                                subtitle = if (topic.entryCount == 0) {
                                    Microcopy.TOPIC_EMPTY_SUBTITLE
                                } else {
                                    // T-14：topicCardSubtitle 签名改（修订二 §16.2 条数在前）的编译机械跟随；
                                    // 本调用行随 T-16 分家消亡（主题分区整体移除）
                                    Microcopy.topicCardSubtitle(
                                        topic.entryCount,
                                        archiveRepository.formatTimestamp(Date(topic.createdAt))
                                    )
                                },
                                index = index,
                                // 非空主题滑动删除恒不触发（85(a)：零响应——右向位移钳 0，无滑动反馈）；
                                // 仅空主题（0 条）启用右滑删除
                                swipeToDeleteEnabled = topic.entryCount == 0,
                                onClick = { onFileClick(topic.file, ArchiveEntryKind.TOPIC) },
                                onLongClick = {
                                    performVibration(context, VIBRATION_DURATION_SHORT_MS)
                                    // Copy all content from topic（与归档长按复制同款：parse＋formatForExport，
                                    // 需求 85 出口铁律——无 ##/---/# Appendo 结构标记、无 ZWSP）
                                    try {
                                        val content = EntryParser.formatForExport(
                                            EntryParser.parse(topic.file.readText())
                                        )
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("topic", content))
                                        showToast(context, Microcopy.TOAST_COPIED_ALL)
                                    } catch (e: Exception) {
                                        if (BuildConfig.DEBUG) {
                                            android.util.Log.e("ArchiveListScreen", "Failed to copy topic", e)
                                        }
                                        showToast(context, "复制失败")
                                    }
                                },
                                onSwipeToDelete = {
                                    performVibration(context, VIBRATION_DURATION_LONG_MS)
                                    topicToDelete = topic
                                    showTopicDeleteDialog = true
                                },
                                onSwipeLeftRestore = {
                                    performVibration(context, VIBRATION_DURATION_SHORT_MS)
                                    restoreFile = topic.file
                                    restoreName = topic.displayName
                                    restoreKind = ArchiveEntryKind.TOPIC
                                    restoreEntryCount = topic.entryCount
                                    showRestoreDialog = true
                                }
                            )
                        }
                    }
                    if (archives.isNotEmpty()) {
                        item(key = "section_archives") {
                            SectionHeader(Microcopy.SECTION_ARCHIVES)
                        }
                        items(
                            items = archives,
                            key = { it.file.absolutePath }
                        ) { archive ->
                            val index = archives.indexOf(archive)
                            ArchiveCard(
                                title = archive.name.removeSuffix(".md"),
                                subtitle = "${archiveRepository.formatTimestamp(archive.timestamp)} · ${archive.entryCount} 条",
                                index = index,
                                onClick = { onFileClick(archive.file, ArchiveEntryKind.ARCHIVE) },
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
                                    archiveToDeleteIndex = index
                                    showDeleteDialog = true
                                },
                                onSwipeLeftRestore = {
                                    performVibration(context, VIBRATION_DURATION_SHORT_MS)
                                    restoreFile = archive.file
                                    restoreName = archive.name
                                    restoreKind = ArchiveEntryKind.ARCHIVE
                                    restoreEntryCount = archive.entryCount
                                    showRestoreDialog = true
                                }
                            )
                        }
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
                archiveToDeleteIndex = -1
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
                        archiveToDeleteIndex = -1
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
                        archiveToDeleteIndex = -1
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // Delete confirmation dialog（空主题，§8.3：error 红标题＋红「删除」按钮，78(a) 破坏性=红）
    if (showTopicDeleteDialog && topicToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showTopicDeleteDialog = false
                topicToDelete = null
            },
            title = {
                Text(
                    Microcopy.DLG_TOPIC_DELETE_TITLE,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(Microcopy.dlgTopicDeleteBody(topicToDelete!!.displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = topicToDelete!!
                        // 删除走 TopicRepository.deleteTopic（文件＋SP key 同清）；SP commit 同步盘，
                        // Dispatchers.IO 对齐 T-06 事务先例（§8.3）
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                topicRepository.deleteTopic(target)
                            }
                            when (result) {
                                TopicDeleteResult.Deleted -> {
                                    showToast(context, "已删除")
                                    loadFiles()
                                }
                                // NotEmpty（确认停留期间被移入）/ Failed（IO 失败）→ 统一失败 toast＋重载如实反映
                                else -> {
                                    showToast(context, Microcopy.TOAST_TOPIC_DELETE_FAILED)
                                    loadFiles()
                                }
                            }
                            showTopicDeleteDialog = false
                            topicToDelete = null
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTopicDeleteDialog = false
                        topicToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // Restore confirmation dialog（归档/主题共用件，§8.4：主题差异仅注明行＋同字面 toast）
    if (showRestoreDialog && restoreFile != null) {
        RestoreConfirmDialog(
            file = restoreFile!!,
            sourceLabel = if (restoreKind == ArchiveEntryKind.TOPIC) "主题" else "归档",
            sourceName = restoreName,
            entryCount = restoreEntryCount,
            fileRepository = fileRepository,
            restoreNote = if (restoreKind == ArchiveEntryKind.TOPIC) {
                Microcopy.TOPIC_RESTORE_NOTE
            } else {
                null
            },
            onDismiss = {
                showRestoreDialog = false
                restoreFile = null
                restoreEntryCount = 0
            },
            onConfirmed = {
                showRestoreDialog = false
                restoreFile = null
                restoreEntryCount = 0
                // §8.3：恢复/删除操作后重载（主题恢复后显式重载）
                loadFiles()
            }
        )
    }
}

/** 分区标题（§8.3）：labelMedium＋primary——分区标题属组件级用途、非正文，P8 允许。 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 归档/主题卡片（T-13 起为薄布局包装，设计修订二 §14.1/§15.1 A8）：滑动手势骨架下沉 [SwipeCard]
 * 共用，本件仅保留内容行布局（图标＋标题/副文案）；调用点签名与手势行为零变化（行为等价红线 R16）。
 */
@Composable
private fun ArchiveCard(
    title: String,
    subtitle: String,
    index: Int,
    swipeToDeleteEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
    onSwipeLeftRestore: () -> Unit
) {
    SwipeCard(
        index = index,
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
