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
import com.yiyue31.android.appendo.ui.EntryListScreen
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import com.yiyue31.android.appendo.util.MarkdownFormatter
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import com.yiyue31.android.appendo.util.SafMarkdownFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

// Constants for magic numbers
private const val FILE_CHANGE_POLL_INTERVAL_MS = 2000L
private const val VIBRATION_DURATION_LONG_MS = 200L
private const val VIBRATION_DURATION_SHORT_MS = 100L

// Data class to represent a single entry
data class LinkEntry(
    val timestamp: String,
    val content: String
)

// Function to parse markdown content into entries
fun parseMarkdownEntries(content: String): List<LinkEntry> {
    val entries = mutableListOf<LinkEntry>()
    val lines = content.lines()
    var currentTimestamp = ""
    var currentContent = StringBuilder()

    for (line in lines) {
        when {
            // Check if this is a timestamp line
            MarkdownFormatter.getTimestampRegex().matches(line) -> {
                // Save previous entry if exists
                if (currentTimestamp.isNotEmpty() && currentContent.isNotEmpty()) {
                    entries.add(LinkEntry(currentTimestamp, currentContent.toString().trim()))
                }
                currentTimestamp = line.substring(3).trim()  // Remove "## " prefix
                currentContent = StringBuilder()
            }
            // Skip header and separators
            line.startsWith("# Appendo") || line == "---" -> {
                // Skip these lines
            }
            // Otherwise, add to current content
            else -> {
                if (currentContent.isNotEmpty()) {
                    currentContent.append("\n")
                }
                currentContent.append(line)
            }
        }
    }

    // Save last entry
    if (currentTimestamp.isNotEmpty() && currentContent.isNotEmpty()) {
        entries.add(LinkEntry(currentTimestamp, currentContent.toString().trim()))
    }

    return entries
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    fileRepository: FileRepository,
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
    var inputContent by remember { mutableStateOf("") }
    var lastModified by remember { mutableStateOf(fileRepository.getFileLastModified()) }
    var showMenu by remember { mutableStateOf(false) }

    // Helper function to get current MarkdownFileOperations instance
    fun getCurrentMarkdownFile(): MarkdownFileOperations {
        return MarkdownFileFactory.create(context, useSAF, fileUri, defaultFile)
    }

    // Helper function to refresh entry count and entries from a single file read
    fun refreshEntryCount() {
        coroutineScope.launch {
            try {
                val mdFile = getCurrentMarkdownFile()
                if (!mdFile.exists()) {
                    mdFile.initHeader()
                }
                val content = mdFile.readAll()
                val newEntries = parseMarkdownEntries(content)
                entries = newEntries
                entryCount = newEntries.size
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
                    onEntryLongClick = { content ->
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("entry", content))
                        showToast(context, "已复制")
                    },
                    onEntrySwipeToDelete = { timestamp ->
                        entryToDeleteTimestamp = timestamp
                        showDeleteDialog = true
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
                        text = "版本 1.0.0",
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
                                if (mdFile.append(inputContent)) {
                                    fileRepository.setFileLastModified(System.currentTimeMillis())
                                    refreshEntryCount()
                                    showToast(context, "内容已追加")
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

private fun copyContent(context: android.content.Context, mdFile: MarkdownFileOperations) {
    try {
        val content = mdFile.readAll()
        // Check if content is empty (only has header, no actual entries)
        if (content.isBlank() || !content.contains("## ")) {
            showToast(context, "暂无内容可复制")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("link_collection", content))
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
        val content = mdFile.readAll()
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

        // Create archive file and write current content to it
        val archiveMdFile = FileBasedMarkdownFile(context, archiveFile)
        archiveMdFile.initHeader()

        // Copy current content to archive if not empty
        if (currentContent.isNotBlank()) {
            // Extract content after header
            val contentWithoutHeader = if (currentContent.startsWith(MarkdownFormatter.FILE_HEADER)) {
                currentContent.substring(MarkdownFormatter.FILE_HEADER.length)
            } else {
                currentContent
            }

            // Write to archive
            java.io.FileOutputStream(archiveFile, true).use { output ->
                output.write(contentWithoutHeader.toByteArray(Charsets.UTF_8))
            }
        }

        showToast(context, "已归档到: ${archiveFile.name}")
        onComplete()
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("MainScreen", "Archive failed", e)
        }
        showToast(context, "归档失败: ${e.message}")
    }
}
