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
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Constants for magic numbers
private const val VIBRATION_DURATION_SHORT_MS = 100L
private const val VIBRATION_DURATION_LONG_MS = 200L
private const val SWIPE_THRESHOLD_DP = 120f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveListScreen(
    fileRepository: FileRepository,
    onNavigateBack: () -> Unit,
    onArchiveClick: (ArchiveFile) -> Unit
) {
    val context = LocalContext.current
    val archiveRepository = remember { ArchiveRepository(context) }

    var archives by remember { mutableStateOf<List<ArchiveFile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var archiveToDelete by remember { mutableStateOf<ArchiveFile?>(null) }
    var archiveToDeleteIndex by remember { mutableIntStateOf(-1) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var archiveToRestore by remember { mutableStateOf<ArchiveFile?>(null) }
    var restoreEntryCount by remember { mutableIntStateOf(0) }

    fun loadArchives() {
        isLoading = true
        // Load archives in IO context
        archives = archiveRepository.listArchiveFiles()
        isLoading = false
    }

    // Load archives on first launch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            loadArchives()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "归档管理",
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Primary
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
            } else if (archives.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "暂无归档文件",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "归档的文件会显示在这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Text(
                    "共 ${archives.size} 个归档",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = archives,
                        key = { it.file.absolutePath }
                    ) { archive ->
                        val index = archives.indexOf(archive)
                        ArchiveCard(
                            archive = archive,
                            archiveRepository = archiveRepository,
                            index = index,
                            onClick = { onArchiveClick(archive) },
                            onLongClick = {
                                performVibration(context, VIBRATION_DURATION_SHORT_MS)
                                // Copy all content from archive
                                try {
                                    val content = EntryParser.stripIsolationMarkers(archive.file.readText())
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("archive", content))
                                    showToast(context, "已复制全部内容")
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
                                archiveToRestore = archive
                                restoreEntryCount = archive.entryCount
                                showRestoreDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
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
                    color = AppColors.Danger
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
                            loadArchives()
                        } else {
                            showToast(context, "删除失败")
                        }
                        showDeleteDialog = false
                        archiveToDelete = null
                        archiveToDeleteIndex = -1
                    }
                ) {
                    Text("删除", color = AppColors.Danger)
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

    // Restore confirmation dialog
    if (showRestoreDialog && archiveToRestore != null) {
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                archiveToRestore = null
                restoreEntryCount = 0
            },
            title = {
                Text(
                    "追加到当前文档",
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Success
                )
            },
            text = {
                Text("确定要将归档 \"${archiveToRestore!!.name}\" 中的 $restoreEntryCount 条内容追加到当前文档吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val archiveContent = archiveToRestore!!.file.readText()
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
                                skippedCount == 0 -> "已恢复 $addedCount 条"
                                else -> "已恢复 $addedCount 条，跳过 $skippedCount 条已存在"
                            }
                            showToast(context, msg)
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.e("ArchiveListScreen", "Failed to restore archive", e)
                            }
                            showToast(context, "追加失败")
                        }
                        showRestoreDialog = false
                        archiveToRestore = null
                        restoreEntryCount = 0
                    }
                ) {
                    Text("追加", color = AppColors.Success)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        archiveToRestore = null
                        restoreEntryCount = 0
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveCard(
    archive: ArchiveFile,
    archiveRepository: ArchiveRepository,
    index: Int,
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
        offsetX > SWIPE_THRESHOLD_DP / 2 -> AppColors.Danger // Red for delete
        offsetX < -SWIPE_THRESHOLD_DP / 2 -> AppColors.Success // Green for restore
        else -> Color.Transparent
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
                        color = Color.White
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
                                offsetX = newOffset.coerceIn(-SWIPE_THRESHOLD_DP * 1.5f, SWIPE_THRESHOLD_DP * 1.5f)
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
                        tint = AppColors.Primary
                    )

                    Spacer(modifier = Modifier.size(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = archive.name.removeSuffix(".md"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${archiveRepository.formatTimestamp(archive.timestamp)} · ${archive.entryCount} 条",
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
