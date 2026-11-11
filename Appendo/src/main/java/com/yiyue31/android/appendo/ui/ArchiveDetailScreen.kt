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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.data.ArchiveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 归档 / 主题详情页共用（T-07，§8.5；2026-11-11 换机验证裁定）：[kind] = [ArchiveEntryKind.TOPIC] 时
 * 标题＝主题名本身（「详情」二字多余；原 sourceFileName 列表头小字退役——字号过小难认）；归档默认路径
 * （[screenTitle] 缺省「归档详情」＋TopAppBar 文件名小字）显示行为零变化（89(h)）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDetailScreen(
    archive: ArchiveFile,
    onNavigateBack: () -> Unit,
    screenTitle: String = "归档详情",
    kind: ArchiveEntryKind = ArchiveEntryKind.ARCHIVE
) {
    val context = LocalContext.current

    var entries by remember { mutableStateOf(emptyList<LinkEntry>()) }
    var entryCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    // Load archive content
    LaunchedEffect(archive.file.absolutePath) {
        withContext(Dispatchers.IO) {
            try {
                val content = archive.file.readText()
                val parsedEntries = parseMarkdownEntries(content)
                entries = parsedEntries
                entryCount = parsedEntries.size
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("ArchiveDetailScreen", "Failed to load archive", e)
                }
                entries = emptyList()
                entryCount = 0
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 主题路径（2026-11-11 换机验证裁定两步）：标题＝主题名本身（「详情」二字多余——
                    // 返回箭头＋语境已表意；原名仅列表头部小字、字号过小难认）；归档路径维持原状——
                    // 文件名小号灰字跟在「归档详情」后
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (kind == ArchiveEntryKind.TOPIC) {
                                archive.name.removeSuffix(".md")
                            } else {
                                screenTitle
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (kind == ArchiveEntryKind.ARCHIVE) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                archive.name.removeSuffix(".md"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "此归档为空",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Use shared EntryListScreen component (read-only mode)
                EntryListScreen(
                    entries = entries,
                    entryCount = entryCount,
                    readOnly = true,
                    // 主题名已并入顶栏标题（2026-11-11 裁定），sourceFileName 列表头小字退役
                    onEntryLongClick = { content ->
                        // Vibration is handled by EntryListScreen
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("archive_entry", content))
                        // 常量化归位（T-06 核验建议 D6 同款）
                        showToast(context, Microcopy.TOAST_COPIED)
                    },
                    onEntrySwipeToDelete = {
                        // Should not be called in read-only mode
                    }
                )
            }
        }
    }
}
