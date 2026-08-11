package com.yiyue31.android.appendo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.reminder.AlarmScheduler
import com.yiyue31.android.appendo.reminder.ReminderStore
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.yiyue31.android.appendo.reminder.ReminderIntents
import com.yiyue31.android.appendo.util.ReminderMeta
import com.yiyue31.android.appendo.util.ReminderLogic
import com.yiyue31.android.appendo.ui.EntryListScreen
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.util.CalendarEntryMapper
import com.yiyue31.android.appendo.util.CalendarLauncher
import com.yiyue31.android.appendo.util.DuplicateHintThrottle
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import com.yiyue31.android.appendo.util.MarkdownFormatter
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import com.yiyue31.android.appendo.util.SafMarkdownFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Constants for magic numbers
private const val FILE_CHANGE_POLL_INTERVAL_MS = 2000L
private const val VIBRATION_DURATION_LONG_MS = 200L
private const val VIBRATION_DURATION_SHORT_MS = 100L

// parseMarkdownEntries 委托给 EntryParser（v1.1 收敛）；保留至 T-016 迁移现有调用方后删除。
fun parseMarkdownEntries(content: String): List<LinkEntry> =
    EntryParser.parse(content).map { LinkEntry.from(it) }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    fileRepository: FileRepository,
    scrollToTs: String? = null,
    onNavigateToArchiveList: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var useSAF by remember { mutableStateOf(fileRepository.isUsingSAF()) }
    var fileUri by remember { mutableStateOf(fileRepository.getFileUri()) }
    var defaultFile by remember { mutableStateOf(fileRepository.getDefaultFile()) }
    var entryCount by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf(emptyList<LinkEntry>()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSelfCheck by remember { mutableStateOf(false) }
    var inputContent by remember { mutableStateOf("") }
    var lastModified by remember { mutableStateOf(fileRepository.getFileLastModified()) }
    var showMenu by remember { mutableStateOf(false) }
    var showSetupGuideDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<LinkEntry?>(null) }
    var editContent by remember { mutableStateOf("") }

    // 深链：从提醒通知点按进来后，滚动到该记录（仅滚一次）
    val entryListState = rememberLazyListState()
    val scrolledTs = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollToTs, entries) {
        val ts = scrollToTs ?: return@LaunchedEffect
        if (ts == scrolledTs.value) return@LaunchedEffect
        val idx = entries.sortedByDescending { it.timestamp }.indexOfFirst { it.timestamp == ts }
        if (idx >= 0) {
            entryListState.animateScrollToItem(idx)
            scrolledTs.value = ts
        }
    }

    // —— 提醒（C 方案）——
    var showReminderPicker by remember { mutableStateOf(false) }
    var pendingReminderTs by remember { mutableStateOf<String?>(null) }
    var showOverwriteReminder by remember { mutableStateOf(false) }
    var pendingSchedule by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val p = pendingSchedule
        pendingSchedule = null
        if (p != null) {
            if (granted) scheduleReminder(context, p.first, p.second)
            else showToast(context, "未授予通知权限，提醒将不会显示")
        }
    }

    fun startSetReminder(ts: String, triggerAt: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingSchedule = ts to triggerAt
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleReminder(context, ts, triggerAt)
        }
    }

    // Helper function to get current MarkdownFileOperations instance
    fun getCurrentMarkdownFile(): MarkdownFileOperations {
        return MarkdownFileFactory.create(context, useSAF, fileUri, defaultFile)
    }

    // Helper function to refresh entry count and entries from a single file read
    fun refreshEntryCount() {
        coroutineScope.launch {
            try {
                // 文件 I/O + 解析下放 Dispatchers.IO（中-7：避免主线程 ANR，尤其 2s 轮询路径）
                val (newEntries, recovered) = withContext(Dispatchers.IO) {
                    val mdFile = getCurrentMarkdownFile()
                    if (!mdFile.exists()) {
                        mdFile.initHeader()
                    }
                    val result = mdFile.readAllWithStatus()
                    Pair(parseMarkdownEntries(result.content), result.recovered)
                }
                if (recovered) {
                    // SAF 软恢复发生时透明提示（specs 46）；默认模式 recovered 恒 false
                    showToast(context, "检测到上次异常退出，已从备份恢复，请检查近期改动")
                }
                entries = newEntries
                entryCount = newEntries.size
                // 对账：清掉 sidecar 中已不存在的条目（外部编辑/换文件导致的孤儿提醒）
                val timestamps = newEntries.map { it.timestamp }.toSet()
                val store = ReminderStore.get(context)
                val orphans = ReminderLogic.findOrphans(store.allKeys(), timestamps)
                if (orphans.isNotEmpty()) {
                    orphans.forEach { ts ->
                        AlarmScheduler.cancel(context, ts)
                        store.remove(ts)
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("MainScreen", "Failed to refresh entries", e)
                }
                entryCount = 0
                entries = emptyList()
            }
        }
    }

    // File selection launcher for changing to SAF mode
    val changeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            fileRepository.saveFileUri(uri)
            useSAF = true
            fileUri = uri
            fileRepository.clearFileLastModified()
            refreshEntryCount()
        }
    }

    // Launcher for opening an existing file from external storage (setup guide)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            fileRepository.saveFileUri(uri)
            useSAF = true
            fileUri = uri
            fileRepository.clearFileLastModified()
            refreshEntryCount()
            showSetupGuideDialog = false
        }
    }

    // Launcher for creating a new file in external storage (setup guide)
    val createExternalFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            fileRepository.saveFileUri(uri)
            useSAF = true
            fileUri = uri
            fileRepository.clearFileLastModified()
            // Initialize header for new file
            val mdFile = getCurrentMarkdownFile()
            if (!mdFile.exists()) {
                mdFile.initHeader()
            }
            refreshEntryCount()
            showSetupGuideDialog = false
        }
    }

    // Initialize on first launch
    LaunchedEffect(Unit) {
        // Check if SAF URI is still valid
        if (useSAF) {
            val uri = fileUri
            if (uri == null || !fileRepository.isFileUriValid()) {
                fileRepository.clearFileUri()
                useSAF = false
                fileUri = null
                defaultFile = fileRepository.getDefaultFile()
            }
        }

        // Ensure default file exists
        if (!useSAF) {
            val mdFile = getCurrentMarkdownFile()
            if (!mdFile.exists()) {
                mdFile.initHeader()
            }
        }

        refreshEntryCount()

        // Show setup guide on fresh install
        if (fileRepository.isFirstLaunch()) {
            showSetupGuideDialog = true
        }
    }

    // Poll for file changes every 2 seconds with proper cancellation
    LaunchedEffect(Unit) {
        while (isActive) {  // IMPORTANT: Check isActive to prevent memory leak
            delay(FILE_CHANGE_POLL_INTERVAL_MS)
            val currentModified = fileRepository.getFileLastModified()
            if (currentModified != lastModified) {
                lastModified = currentModified
                refreshEntryCount()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Appendo",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Primary
                        )
                        Text(
                            "已收集 $entryCount 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "菜单"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("归档") },
                            onClick = {
                                showMenu = false
                                archiveFile(context, fileRepository, getCurrentMarkdownFile(), fileRepository.getArchiveFile()) {
                                    useSAF = fileRepository.isUsingSAF()
                                    fileUri = fileRepository.getFileUri()
                                    defaultFile = fileRepository.getDefaultFile()
                                    fileRepository.clearFileLastModified()
                                    refreshEntryCount()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("归档管理") },
                            onClick = {
                                showMenu = false
                                onNavigateToArchiveList()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("打开文件") },
                            onClick = {
                                showMenu = false
                                openFile(context, useSAF, fileUri, defaultFile)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("自定义目录") },
                            onClick = {
                                showMenu = false
                                changeFileLauncher.launch("Appendo.md")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("提醒自检") },
                            onClick = {
                                showMenu = false
                                showSelfCheck = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("关于") },
                            onClick = {
                                showMenu = false
                                showAboutDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Button grid - 2x2 layout
            // First row: Manual Input and Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        showInputDialog = true
                        // Auto-show keyboard
                        keyboardController?.show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        "手动输入",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = {
                        copyContent(context, getCurrentMarkdownFile())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, AppColors.Primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.Primary
                    )
                ) {
                    Text("复制全部", fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Second row: Share and Clear (Long press)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        shareContent(context, getCurrentMarkdownFile())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, AppColors.Primary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.Primary
                    )
                ) {
                    Text("分享全部", fontWeight = FontWeight.Medium)
                }

                // Custom clear button with long-press support
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .combinedClickable(
                            onClick = {
                                // Show hint for long press
                                showToast(context, "长按以清空内容")
                            },
                            onLongClick = {
                                // Vibrate feedback (with hasVibrator check)
                                performVibration(context, VIBRATION_DURATION_LONG_MS)
                                showClearDialog = true
                            },
                            onLongClickLabel = "长按清空"
                        )
                        .background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, AppColors.Danger.copy(alpha = 0.7f)),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "清空",
                                fontWeight = FontWeight.Medium,
                                color = AppColors.Danger
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Content section
            if (entries.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "暂无收集内容",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "从任意应用分享链接或文字\n到本应用即可自动收集",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                // Entry list with shared component
                var showDeleteDialog by remember { mutableStateOf(false) }
                // Store timestamp instead of index for reliable deletion
                var entryToDeleteTimestamp by remember { mutableStateOf("") }

                EntryListScreen(
                    entries = entries,
                    entryCount = entryCount,
                    listState = entryListState,
                    onEntryLongClick = { content ->
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("entry", content))
                        showToast(context, "已复制")
                    },
                    onEntrySwipeToDelete = { timestamp ->
                        entryToDeleteTimestamp = timestamp
                        showDeleteDialog = true
                    },
                    onEntryClick = { entry ->
                        selectedEntry = entry
                        editContent = entry.content
                        showDetailDialog = true
                    }
                )

                // Delete entry confirmation dialog
                if (showDeleteDialog && entryToDeleteTimestamp.isNotEmpty()) {
                    AlertDialog(
                        onDismissRequest = {
                            showDeleteDialog = false
                            entryToDeleteTimestamp = ""
                        },
                        title = {
                            Text(
                                "删除条目",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.Danger
                            )
                        },
                        text = {
                            Text("确定要删除这条内容吗？\n\n此操作无法撤销。")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showDeleteDialog = false
                                    try {
                                        val mdFile = getCurrentMarkdownFile()

                                        if (BuildConfig.DEBUG) {
                                            android.util.Log.d("MainScreen", "Attempting to delete entry with timestamp: $entryToDeleteTimestamp")
                                            android.util.Log.d("MainScreen", "Current entry count: ${mdFile.count()}")
                                        }

                                        // Delete entry by timestamp
                                        if (mdFile.deleteEntry(entryToDeleteTimestamp)) {
                                            if (BuildConfig.DEBUG) {
                                                android.util.Log.d("MainScreen", "Delete successful, new count: ${mdFile.count()}")
                                            }
                                            fileRepository.setFileLastModified(System.currentTimeMillis())
                                            refreshEntryCount()
                                            showToast(context, "已删除")
                                            // 联动取消该条提醒
                                            AlarmScheduler.cancel(context, entryToDeleteTimestamp)
                                            ReminderStore.get(context).remove(entryToDeleteTimestamp)
                                        } else {
                                            if (BuildConfig.DEBUG) {
                                                android.util.Log.e("MainScreen", "Delete returned false")
                                            }
                                            showToast(context, "删除失败")
                                        }
                                    } catch (e: Exception) {
                                        if (BuildConfig.DEBUG) {
                                            android.util.Log.e("MainScreen", "Failed to delete entry", e)
                                        }
                                        showToast(context, "删除失败")
                                    }
                                    entryToDeleteTimestamp = ""
                                }
                            ) {
                                Text("删除", color = AppColors.Danger)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showDeleteDialog = false
                                    entryToDeleteTimestamp = ""
                                }
                            ) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
        }
    }

    // Setup guide dialog (first launch)
    if (showSetupGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSetupGuideDialog = false },
            title = {
                Text(
                    "选择存储位置",
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Primary
                )
            },
            text = {
                Column {
                    Text(
                        "建议将数据文件保存在外部存储位置，这样重装应用后数据不会丢失。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "你可以：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• 打开已有文件 — 恢复之前保存的数据\n• 新建文件 — 在外部位置创建新文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openFileLauncher.launch(arrayOf("text/markdown", "text/*"))
                    }
                ) {
                    Text("打开已有文件", color = AppColors.Primary)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        createExternalFileLauncher.launch("Appendo.md")
                    }) {
                        Text("新建文件")
                    }
                    TextButton(onClick = { showSetupGuideDialog = false }) {
                        Text("暂不设置")
                    }
                }
            }
        )
    }

    // About dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    "关于 Appendo",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Appendo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "功能简介",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• 通过分享收集来自其他应用的链接和文本\n• 手动输入快速记录内容\n• 实时预览已收集的条目\n• 一键复制、分享、清空内容",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "作者: yiyue31",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // Clear confirmation dialog - with backup option
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "清空内容",
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Danger
                )
            },
            text = {
                Text(
                    "将自动备份当前内容到新文件，然后清空当前文件。\n\n确定要继续吗？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        try {
                            val mdFile = getCurrentMarkdownFile()

                            // First, create a backup archive
                            archiveFile(context, fileRepository, mdFile, fileRepository.getArchiveFile()) {
                                useSAF = fileRepository.isUsingSAF()
                                fileUri = fileRepository.getFileUri()
                                defaultFile = fileRepository.getDefaultFile()
                            }

                            // Then clear current file
                            if (mdFile.clear()) {
                                fileRepository.setFileLastModified(System.currentTimeMillis())
                                entryCount = 0
                                entries = emptyList()
                                showToast(context, "已清空")
                                // 联动清空所有提醒（cancel 全部闹钟 + 清 sidecar）
                                val store = ReminderStore.get(context)
                                store.allKeys().forEach { AlarmScheduler.cancel(context, it) }
                                store.removeAll()
                            } else {
                                showToast(context, "清空失败")
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.e("MainScreen", "Failed to clear", e)
                            }
                            showToast(context, "操作失败")
                        }
                    }
                ) {
                    Text("确定", color = AppColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Detail/edit dialog
    if (showDetailDialog && selectedEntry != null) {
        val scrollState = rememberScrollState()

        AlertDialog(
            onDismissRequest = {
                showDetailDialog = false
                selectedEntry = null
                editContent = ""
            },
            title = {
                Text(
                    "编辑条目",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = selectedEntry!!.timestampDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 10,
                        placeholder = { Text("无内容") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // C 设提醒（主操作）
                    OutlinedButton(
                        onClick = {
                            val ts = selectedEntry!!.timestamp
                            pendingReminderTs = ts
                            if (ReminderStore.get(context).hasUnfired(ts)) {
                                showOverwriteReminder = true
                            } else {
                                showReminderPicker = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, AppColors.Primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                    ) {
                        Text("⏰ 设提醒", fontWeight = FontWeight.Medium)
                    }
                    // A 添加到日历（次要，缩小）
                    TextButton(
                        onClick = {
                            if (editContent.isBlank()) {
                                showToast(context, "内容不能为空")
                                return@TextButton
                            }
                            val entry = CalendarEntryMapper.map(editContent)
                            if (!CalendarLauncher.launch(context, entry)) {
                                showToast(context, "未找到日历应用")
                            }
                        }
                    ) {
                        Text("添加到日历", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editContent.isBlank()) {
                            showToast(context, "内容不能为空")
                            return@TextButton
                        }
                        if (editContent == selectedEntry!!.content) {
                            showDetailDialog = false
                            selectedEntry = null
                            editContent = ""
                            return@TextButton
                        }
                        try {
                            val mdFile = getCurrentMarkdownFile()
                            if (mdFile.updateEntry(selectedEntry!!.timestamp, editContent)) {
                                fileRepository.setFileLastModified(System.currentTimeMillis())
                                refreshEntryCount()
                                showToast(context, "已保存")
                            } else {
                                showToast(context, "保存失败")
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.e("MainScreen", "Failed to update entry", e)
                            }
                            showToast(context, "保存失败")
                        }
                        showDetailDialog = false
                        selectedEntry = null
                        editContent = ""
                    }
                ) {
                    Text("保存", color = AppColors.Primary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDetailDialog = false
                        selectedEntry = null
                        editContent = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 提醒自检（验证本机提醒能否触发：通知权限 + 电池白名单 + 测试提醒）
    if (showSelfCheck) {
        val notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val batteryOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(android.os.PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        } else true
        AlertDialog(
            onDismissRequest = { showSelfCheck = false },
            title = { Text("提醒自检") },
            text = {
                Column {
                    Text(if (notifEnabled) "通知权限：✅ 已开启" else "通知权限：❌ 未开启（提醒不会显示）")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (batteryOk) "后台运行：✅ 已加白名单"
                        else "后台运行：❌ 未加白名单（realme 等可能杀后台，导致提醒不响）"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("自启动：⚠️ 无法自动检测——重启后提醒的重注册依赖它；realme 需到 应用设置→自启动 开启")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "点“发送测试提醒”，约 1 分钟后若响起 = 此机支持本地提醒（短时正证明）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AlarmScheduler.schedule(
                        context,
                        ReminderIntents.TEST_REMINDER_TS,
                        System.currentTimeMillis() + 60_000L
                    )
                    showToast(context, "测试提醒已排，约 1 分钟后响")
                }) { Text("发送测试提醒") }
            },
            dismissButton = {
                Row {
                    if (!batteryOk) {
                        TextButton(onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                showToast(context, "无法跳转，请到系统设置手动允许后台运行")
                            }
                        }) { Text("允许后台运行") }
                    }
                    TextButton(onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            showToast(context, "无法跳转，请手动到设置开启自启动")
                        }
                    }) { Text("自启动设置") }
                    TextButton(onClick = { showSelfCheck = false }) { Text("关闭") }
                }
            }
        )
    }

    // 提醒时间选择
    if (showReminderPicker) {
        val ts = pendingReminderTs
        if (ts != null) {
            ReminderTimePickerDialog(
                onPick = { triggerAt ->
                    showReminderPicker = false
                    if (triggerAt <= System.currentTimeMillis()) {
                        showToast(context, "请选择未来时间")
                    } else {
                        startSetReminder(ts, triggerAt)
                    }
                },
                onDismiss = { showReminderPicker = false }
            )
        }
    }

    // 覆盖确认（已有提醒时）
    if (showOverwriteReminder) {
        AlertDialog(
            onDismissRequest = {
                showOverwriteReminder = false
                pendingReminderTs = null
            },
            title = { Text("替换已有提醒？") },
            text = { Text("该记录已设提醒，确定替换为新时间？") },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteReminder = false
                    showReminderPicker = true
                }) { Text("替换") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverwriteReminder = false
                    pendingReminderTs = null
                }) { Text("取消") }
            }
        )
    }

    // Manual input dialog
    if (showInputDialog) {
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            // Focus the text field and show keyboard when dialog opens
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        AlertDialog(
            onDismissRequest = {
                showInputDialog = false
                inputContent = ""
                // Hide keyboard when dialog closes
                keyboardController?.hide()
            },
            title = {
                Text(
                    "手动输入内容",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = inputContent,
                    onValueChange = { inputContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("请输入要追加的内容") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputContent.isNotBlank()) {
                            try {
                                val mdFile = getCurrentMarkdownFile()
                                // 追加前查重（按内容，忽略时间戳）
                                val existingSameCount = parseMarkdownEntries(mdFile.readAll())
                                    .count { it.content == inputContent }
                                if (mdFile.append(inputContent)) {
                                    fileRepository.setFileLastModified(System.currentTimeMillis())
                                    refreshEntryCount()
                                    // 非阻塞提示：先确认成功，再旁注重复（5s 节流防刷屏，specs 42）
                                    val msg = if (existingSameCount > 0 &&
                                        DuplicateHintThrottle.shouldShow(inputContent)
                                    ) {
                                        "已追加（已有相同内容 $existingSameCount 条）"
                                    } else {
                                        "内容已追加"
                                    }
                                    showToast(context, msg)
                                } else {
                                    showToast(context, "追加失败")
                                }
                            } catch (_: Exception) {
                                showToast(context, "追加失败")
                            }
                        }
                        inputContent = ""
                        showInputDialog = false
                        keyboardController?.hide()
                    }
                ) {
                    Text("追加", color = AppColors.Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    inputContent = ""
                    showInputDialog = false
                    keyboardController?.hide()
                }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Perform vibration with safety checks.
 * Checks hasVibrator() before vibrating.
 */
private fun performVibration(context: android.content.Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
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
            android.util.Log.e("MainScreen", "Vibration failed", e)
        }
        // Ignore vibration errors - not critical
    }
}

private fun scheduleReminder(context: android.content.Context, ts: String, triggerAt: Long) {
    AlarmScheduler.schedule(context, ts, triggerAt)
    ReminderStore.get(context).set(ts, ReminderMeta(triggerAt = triggerAt, fired = false, snoozedUntil = 0))
    showToast(context, "已设置提醒")
}

private fun copyContent(context: android.content.Context, mdFile: MarkdownFileOperations) {
    try {
        val content = mdFile.readAllForExternal() // 出口剥离 ZWSP，保证复制出去的内容干净（specs 38，B2）
        // Check if content is empty (only has header, no actual entries)
        if (content.isBlank() || !content.contains("## ")) {
            showToast(context, "暂无内容可复制")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("appendo", content))
        showToast(context, "已复制到剪贴板")
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("MainScreen", "Copy failed", e)
        }
        showToast(context, "复制失败")
    }
}

private fun shareContent(context: android.content.Context, mdFile: MarkdownFileOperations) {
    try {
        val content = mdFile.readAllForExternal() // 出口剥离 ZWSP，保证分享出去的内容干净（specs 38，B2）
        // Check if content is empty (only has header, no actual entries)
        if (content.isBlank() || !content.contains("## ")) {
            showToast(context, "暂无内容可分享")
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "分享内容"))
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("MainScreen", "Share failed", e)
        }
        showToast(context, "分享失败")
    }
}

private fun openFile(context: android.content.Context, useSAF: Boolean, fileUri: Uri?, defaultFile: File) {
    try {
        val uri = if (useSAF && fileUri != null) {
            fileUri
        } else {
            android.net.Uri.fromFile(defaultFile)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/markdown")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("MainScreen", "Open file failed", e)
        }
        showToast(context, "打开失败，请安装 Markdown 查看器")
    }
}

/**
 * Archive current content to a new file.
 * CRITICAL FIX: Now actually copies content to archive before clearing.
 */
private fun archiveFile(
    context: android.content.Context,
    fileRepository: FileRepository,
    currentMdFile: MarkdownFileOperations,
    archiveFile: File,
    onComplete: () -> Unit
) {
    try {
        // Read current content BEFORE creating new archive file
        val currentContent = currentMdFile.readAll()

        // Check if content is empty (only has header, no actual entries)
        if (currentContent.isBlank() || !currentContent.contains("## ")) {
            showToast(context, "暂无内容可归档")
            return
        }

        // 通过 MarkdownFileOperations.writeAll 写归档（v1.1 CB6：享 atomicWrite + 锁 + 同源同格式 ZWSP）
        val contentWithoutHeader = if (currentContent.startsWith(MarkdownFormatter.FILE_HEADER)) {
            currentContent.substring(MarkdownFormatter.FILE_HEADER.length)
        } else {
            currentContent
        }
        val archiveMdFile = FileBasedMarkdownFile(context, archiveFile)
        archiveMdFile.writeAll(MarkdownFormatter.FILE_HEADER + contentWithoutHeader)

        showToast(context, "已归档到: ${archiveFile.name}")
        onComplete()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("MainScreen", "Archive failed", e)
        }
        showToast(context, "归档失败: ${e.message}")
    }
}
