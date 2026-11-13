package com.yiyue31.android.appendo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主题工作区屏骨架（戊批次 T-25，需求 97，设计 §22.1——L1 期 R26 子集）：主题条目列表
 * ＋顶栏（返回＋主题名 primary SemiBold＋同行「N 条」小字 97(f) 计数镜像）。
 *
 * - **L1 期无出口/新增动作**（§25.4 交付切分方案②：复制/分享/新增/编辑/删除＝L2/L3；
 *   T-26 多选两钮随 MoveFlow 落）；
 * - 列表 [EntryListScreen]（readOnly=false——提醒徽标正向启用，K13 定例「只读页不显徽标」
 *   的工作区反向；L1 期动作回调缺省空实现＝骨架零动作面）；headerText 空串＝不渲染列表头
 *   （计数已在顶栏，§22.1 headerText=null 口径）；
 * - 加载：[LaunchedEffect]＋[Dispatchers.IO] 经 [FileBasedMarkdownFile.readAllWithStatus]；
 *   **读失败如实呈现**（TOPIC_READ_FAILED＋BTN_RETRY 重试，评审 C3——不得沿「读失败呈空」旧例）；
 * - 空态：TOPIC_WORKSPACE_EMPTY_*（L1 中性句——无新增钮可指；L3 T-31 切引导句，§24.1）；
 * - 深链定位（101(a)）：[scrollToTs] 命中项滚动（LaunchedEffect 条目载入后一次）；
 * - 不引入轮询（97(f)：L2+ 写路径全应用内、操作后显式重载；骨架期只读无写）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicWorkspaceScreen(
    filePath: String,
    scrollToTs: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val topicFile = remember(filePath) { File(filePath) }
    val topicName = remember(filePath) { topicFile.name.removeSuffix(".md") }
    // null＝加载中；空表＝空态（正常态，0 条主题合法）
    var entries by remember(filePath) { mutableStateOf<List<LinkEntry>?>(null) }
    var loadFailed by remember(filePath) { mutableStateOf(false) }
    var reloadKey by remember(filePath) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(filePath, reloadKey) {
        entries = null
        loadFailed = false
        val result = withContext(Dispatchers.IO) {
            runCatching { FileBasedMarkdownFile(context, topicFile).readAllWithStatus() }.getOrNull()
        }
        if (result == null || result.failed) {
            loadFailed = true
        } else {
            entries = EntryParser.parse(result.content).map { LinkEntry.from(it) }
        }
    }

    // 深链滚动定位（101(a)，§20.4）：条目载入后滚到 scrollTs 命中项（miss 不滚不弹错——
    // architecture.md §5.6 口径）
    LaunchedEffect(entries, scrollToTs) {
        val ts = scrollToTs ?: return@LaunchedEffect
        val loaded = entries ?: return@LaunchedEffect
        val index = loaded.indexOfFirst { it.timestamp == ts }
        if (index >= 0) listState.scrollToItem(index)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            topicName,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            Microcopy.topicEntryCountLine(entries?.size ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
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
                    // v1.4 视觉裁定：顶栏随页面底色，与内容区无缝
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
            when {
                loadFailed -> {
                    // 读失败如实呈现（评审 C3）＋重试（readAllWithStatus 重跑）
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Microcopy.TOPIC_READ_FAILED,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.padding(top = 12.dp))
                            Button(onClick = { reloadKey++ }) {
                                Text(Microcopy.BTN_RETRY)
                            }
                        }
                    }
                }
                entries == null -> {
                    // 加载中（沿 ArchiveDetailScreen「加载中...」先例）
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("加载中...")
                    }
                }
                entries!!.isEmpty() -> {
                    // L1 中性句空态（§24.1：L3 T-31 切「点右上角＋添加条目」引导句）
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Microcopy.TOPIC_WORKSPACE_EMPTY_TITLE,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (Microcopy.TOPIC_WORKSPACE_EMPTY_BODY.isNotBlank()) {
                                Text(
                                    Microcopy.TOPIC_WORKSPACE_EMPTY_BODY,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                else -> {
                    EntryListScreen(
                        entries = entries!!,
                        entryCount = entries!!.size,
                        listState = listState,
                        readOnly = false, // 徽标正向启用（K13 反向）；动作回调缺省＝L1 骨架零动作面
                        headerText = "" // 计数已在顶栏（§22.1 headerText=null 口径）
                    )
                }
            }
        }
    }
}
