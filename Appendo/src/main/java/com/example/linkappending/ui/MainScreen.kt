package com.example.linkappending.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.linkappending.data.FileRepository
import com.example.linkappending.util.SafMarkdownFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            line.matches(Regex("^## \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$")) -> {
                // Save previous entry if exists
                if (currentTimestamp.isNotEmpty() && currentContent.isNotEmpty()) {
                    entries.add(LinkEntry(currentTimestamp, currentContent.toString().trim()))
                }
                currentTimestamp = line.substring(3).trim()  // Remove "## " prefix
                currentContent = StringBuilder()
            }
            // Skip header and separators
            line.startsWith("# Link Collection") || line == "---" -> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(fileRepository: FileRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var fileUri by remember { mutableStateOf(fileRepository.getFileUri()) }
    var entryCount by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf(emptyList<LinkEntry>()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var inputContent by remember { mutableStateOf("") }
    var lastModified by remember { mutableStateOf(fileRepository.getFileLastModified()) }
    var fileName by remember { mutableStateOf("未选择文件") }
    var showMenu by remember { mutableStateOf(false) }

    // Helper function to refresh entry count and entries
    fun refreshEntryCount() {
        coroutineScope.launch {
            val uri = fileUri
            if (uri == null) {
                fileName = "未选择文件"
                entryCount = 0
                entries = emptyList()
                return@launch
            }

            try {
                val mdFile = SafMarkdownFile(context, uri)
                val exists = mdFile.exists()

                if (exists) {
                    val count = mdFile.count()
                    entryCount = count
                    fileName = mdFile.getFileName()

                    // Read and parse file content
                    val content = mdFile.readAll()
                    entries = parseMarkdownEntries(content)
                } else {
                    mdFile.initHeader()
                    entryCount = 0
                    fileName = mdFile.getFileName()
                    entries = emptyList()
                }
            } catch (e: Exception) {
                fileName = "文件访问失败"
                entryCount = 0
                entries = emptyList()
            }
        }
    }

    // File selection launcher for initial setup
    val selectFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            fileRepository.saveFileUri(uri)
            fileUri = uri
            refreshEntryCount()
        }
    }

    // File selection launcher for changing file
    val changeFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            fileRepository.clearFileUri()
            fileRepository.saveFileUri(uri)
            fileUri = uri
            fileRepository.clearFileLastModified()
            refreshEntryCount()
        }
    }

    // Check for file URI validity on first launch
    LaunchedEffect(Unit) {
        if (fileUri == null) {
            // No file selected, prompt user
            selectFileLauncher.launch("Appendo.md")
        } else {
            // Check if URI is still valid
            if (!fileRepository.isFileUriValid()) {
                fileRepository.clearFileUri()
                fileUri = null
                Toast.makeText(context, "文件已失效，请重新选择", Toast.LENGTH_LONG).show()
                selectFileLauncher.launch("Appendo.md")
            } else {
                refreshEntryCount()
            }
        }
    }

    // Poll for file changes every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
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
                title = { Text("Appendo") },
                actions = {
                    if (fileUri != null) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("更换文件") },
                                onClick = {
                                    showMenu = false
                                    changeFileLauncher.launch("Appendo.md")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("归档") },
                                onClick = {
                                    showMenu = false
                                    // Archive: create new file and replace current
                                    changeFileLauncher.launch("Appendo_${System.currentTimeMillis()}.md")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // File info
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已收集: $entryCount 条",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (fileUri != null) {
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { copyContent(context, fileUri!!) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("一键复制")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showInputDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Create, contentDescription = null)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("手动输入")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { openFile(context, fileUri!!) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("打开文件")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清空")
                    }
                    Button(
                        onClick = { shareContent(context, fileUri!!) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("分享")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                // Content display area
                if (entries.isEmpty()) {
                    Text(
                        "暂无收集内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "已收集内容 (${entries.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries.reversed()) { entry ->  // Show newest first
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = entry.timestamp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = entry.content,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "使用说明",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "从任意应用分享链接或文字到本应用即可自动收集",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空") },
            text = { Text("清空所有已收集的条目，保留文件头。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    val uri = fileUri
                    if (uri != null) {
                        try {
                            val mdFile = SafMarkdownFile(context, uri)
                            if (mdFile.clear()) {
                                fileRepository.setFileLastModified(System.currentTimeMillis())
                                entryCount = 0
                                entries = emptyList()
                                Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "清空失败", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, "清空失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("清空")
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
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text("手动输入内容") },
            text = {
                OutlinedTextField(
                    value = inputContent,
                    onValueChange = { inputContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = { Text("请输入要追加的内容") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputContent.isNotBlank()) {
                            val uri = fileUri
                            if (uri != null) {
                                try {
                                    val mdFile = SafMarkdownFile(context, uri)
                                    if (mdFile.append(inputContent)) {
                                        fileRepository.setFileLastModified(System.currentTimeMillis())
                                        refreshEntryCount()
                                        Toast.makeText(context, "内容已追加", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "追加失败", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "追加失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        inputContent = ""
                        showInputDialog = false
                    }
                ) {
                    Text("追加")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    inputContent = ""
                    showInputDialog = false
                }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun copyContent(context: android.content.Context, uri: Uri) {
    try {
        val mdFile = SafMarkdownFile(context, uri)
        val content = mdFile.readAll()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("link_collection", content))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
    }
}

private fun shareContent(context: android.content.Context, uri: Uri) {
    try {
        val mdFile = SafMarkdownFile(context, uri)
        val content = mdFile.readAll()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "分享内容"))
    } catch (_: Exception) {
        Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
    }
}

private fun openFile(context: android.content.Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/markdown")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    } catch (_: Exception) {
        Toast.makeText(context, "打开失败", Toast.LENGTH_SHORT).show()
    }
}
