package com.yiyue31.android.appendo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.data.ArchiveFile
import com.yiyue31.android.appendo.data.ArchiveRepository
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.TopicDeleteResult
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.ui.theme.onSuccessColor
import com.yiyue31.android.appendo.ui.theme.successColor // 滑动指示背景色（ArchiveCard）仍用
import com.yiyue31.android.appendo.util.EntryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

// Constants for magic numbers
private const val VIBRATION_DURATION_SHORT_MS = 100L
private const val VIBRATION_DURATION_LONG_MS = 200L
private const val SWIPE_THRESHOLD_DP = 120f

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
                                    Microcopy.topicCardSubtitle(
                                        archiveRepository.formatTimestamp(Date(topic.createdAt)),
                                        topic.entryCount
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

@OptIn(ExperimentalFoundationApi::class)
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
    var offsetX by remember { mutableStateOf(0f) }
    val cardElevation by animateDpAsState(
        targetValue = if (index == 0) 8.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )

    // Calculate swipe action text and background
    val swipeActionText = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> "删除"
        offsetX < -SWIPE_THRESHOLD_DP / 2 -> "追加"
        else -> null
    }

    val swipeBackgroundColor = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.colorScheme.error // Red for delete
        offsetX < -SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.successColor // Green for restore
        else -> Color.Transparent
    }

    // 深色主题下容器为亮色变体，文字用深色 onXxx（需求 51/设计 §4）
    val swipeContentColor = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.colorScheme.onError
        offsetX < -SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.onSuccessColor
        else -> Color.White
    }

    AnimatedVisibility(
        visible = true,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = shrinkVertically() + fadeOut()
    ) {
        Box {
            // Background layer for swipe action indicator
            if (swipeActionText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(swipeBackgroundColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = when {
                        offsetX > 0 -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    }
                ) {
                    Text(
                        text = swipeActionText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = swipeContentColor
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.toInt(), 0) }
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onClick()
                        },
                        onLongClick = {
                            onLongClick()
                        }
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX > SWIPE_THRESHOLD_DP) {
                                    onSwipeToDelete()
                                } else if (offsetX < -SWIPE_THRESHOLD_DP) {
                                    onSwipeLeftRestore()
                                }
                                offsetX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val newOffset = offsetX + dragAmount
                                // Allow both positive and negative offset
                                // 右滑删除禁用时（非空主题，85(a) 零响应）：正向位移钳 0——卡片无滑动反馈，
                                // onDragEnd 的 > 阈值分支不可达；负向（左滑恢复）不受限
                                val maxPositive = if (swipeToDeleteEnabled) SWIPE_THRESHOLD_DP * 1.5f else 0f
                                offsetX = newOffset.coerceIn(-SWIPE_THRESHOLD_DP * 1.5f, maxPositive)
                            }
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
    }
}

/**
 * Perform vibration with safety checks.
 * Checks hasVibrator() before vibrating.
 */
private fun performVibration(context: Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Check if vibration is supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(durationMs)
            }
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("ArchiveListScreen", "Vibration failed", e)
        }
        // Ignore vibration errors - not critical
    }
}
