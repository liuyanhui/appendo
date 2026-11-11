package com.yiyue31.android.appendo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AddCircle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.data.ArchiveRepository
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.TopicCreateResult
import com.yiyue31.android.appendo.data.TopicDeleteResult
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.util.EntryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/** 页内新建流程状态机（修订二 §15.1 C5，沿移入流程状态机模式）：null＝未开启；NAMING ⇄ SAME_NAME_PROMPT。 */
private enum class TopicCreateStep { NAMING, SAME_NAME_PROMPT }

/**
 * 主题独立页（topic-move 设计修订二 §14.1/§15.1，T-14，条文 90）：`topics` 路由列表页——
 * 顶栏（标题「主题」＋返回＋「新建主题」常驻含空态）、`listTopics` createdAt 倒序直供、
 * TopicCard（[SwipeCard] 薄包装＋40dp 彩色圆形首字标，色板经 [TopicBadge] 纯函数派生）；
 * 交互四槽：点击→主题详情、长按→复制全文（parse＋formatForExport 出口铁律）、左滑→恢复
 * （[RestoreConfirmDialog] 零改动消费）、空主题右滑→删除（非空钳 0 零响应）；
 * 页级空态＋页内新建状态机（复用 [NewTopicNameDialog] 零改动，同名走 C5 提示分支——
 * 提示非指路，无「移入该主题」按钮）。
 *
 * 加载纪律（E-4）：进入加载与操作后重载一律下放 [Dispatchers.IO]（不沿袭归档页主线程重载）。
 * 颜色全经 colorScheme（首字标色板自 (primary, surface) 双参派生＝R17 受控例外）＋
 * 滑动指示随 SwipeCard 既有；字面经 [Microcopy] 与直写先例（沿 ArchiveListScreen）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListScreen(
    fileRepository: FileRepository,
    onNavigateBack: () -> Unit,
    onTopicClick: (file: File) -> Unit
) {
    val context = LocalContext.current
    val archiveRepository = remember { ArchiveRepository(context) }
    val topicRepository = remember { TopicRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var topics by remember { mutableStateOf<List<TopicDocument>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showTopicDeleteDialog by remember { mutableStateOf(false) }
    var topicToDelete by remember { mutableStateOf<TopicDocument?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var restoreName by remember { mutableStateOf("") }
    var restoreEntryCount by remember { mutableStateOf(0) }

    // —— 页内新建状态机（§15.1 C5）：命名输入由本处持有——同名提示返回命名框时输入保留（可改名）——
    var createStep by remember { mutableStateOf<TopicCreateStep?>(null) }
    var newTopicInput by remember { mutableStateOf("") }
    var sameNameExistingName by remember { mutableStateOf("") }

    /** 重载主题列表（E-4：一律下放 Dispatchers.IO——修正归档页主线程重载路径，不沿袭）。 */
    fun reloadTopics() {
        coroutineScope.launch {
            topics = withContext(Dispatchers.IO) { topicRepository.listTopics() }
            isLoading = false
        }
    }

    // 进入加载（沿先例 LaunchedEffect(Unit)）
    LaunchedEffect(Unit) {
        reloadTopics()
    }

    // 首字标色板（§15.1：运行时自 colorScheme 双参派生——R17 受控例外，同名同色、重启不变）
    val colorScheme = MaterialTheme.colorScheme
    val badgePalette = deriveBadgePalette(colorScheme.primary, colorScheme.surface)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "主题",
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
                actions = {
                    // 新建主题（§15.1 顶栏行）：常驻含空态（B2 裁定「有」）；
                    // 关窗不丢字（2026-11-11）：打开不再清输入——未命名草稿保留，创建成功才清空
                    IconButton(onClick = {
                        createStep = TopicCreateStep.NAMING
                    }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "新建主题")
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
            } else if (topics.isEmpty()) {
                // 页级空态（§15.1：无任何主题时的引导，90）
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            Microcopy.TOPIC_PAGE_EMPTY_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            Microcopy.TOPIC_PAGE_EMPTY_BODY,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = topics,
                        key = { "topic_" + it.file.absolutePath }
                    ) { topic ->
                        TopicCard(
                            topic = topic,
                            badgePalette = badgePalette,
                            // 空主题副文案「空」（可发现性：滑动手势不可见，副文案补位提示可删状态）
                            subtitle = if (topic.entryCount == 0) {
                                Microcopy.TOPIC_EMPTY_SUBTITLE
                            } else {
                                Microcopy.topicCardSubtitle(
                                    topic.entryCount,
                                    archiveRepository.formatTimestamp(Date(topic.createdAt))
                                )
                            },
                            onClick = { onTopicClick(topic.file) },
                            onLongClick = {
                                performVibration(context, VIBRATION_DURATION_SHORT_MS)
                                // 复制全文（与归档长按复制同款：parse＋formatForExport，出口铁律——
                                // 无 ##/---/# Appendo 结构标记、无 ZWSP）
                                try {
                                    val content = EntryParser.formatForExport(
                                        EntryParser.parse(topic.file.readText())
                                    )
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("topic", content))
                                    showToast(context, Microcopy.TOAST_COPIED_ALL)
                                } catch (e: Exception) {
                                    if (BuildConfig.DEBUG) {
                                        android.util.Log.e("TopicListScreen", "Failed to copy topic", e)
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
                                restoreEntryCount = topic.entryCount
                                showRestoreDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // 空主题删除确认（§15.1 交互行：error 红框，78(a) 破坏性=红——消费形态沿 T-07 归档页先例；
    // 删除走 TopicRepository.deleteTopic（文件＋SP key 同清），IO 对齐 T-06 事务先例）
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
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                topicRepository.deleteTopic(target)
                            }
                            when (result) {
                                TopicDeleteResult.Deleted -> {
                                    showToast(context, "已删除")
                                    reloadTopics()
                                }
                                // NotEmpty（确认停留期间被移入）/ Failed（IO 失败）→ 统一失败 toast＋重载如实反映
                                else -> {
                                    showToast(context, Microcopy.TOAST_TOPIC_DELETE_FAILED)
                                    reloadTopics()
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

    // 恢复确认（共用件零改动消费，T-07 语义：主题差异＝「主题」名词＋注明行；恢复后经 IO 重载）
    if (showRestoreDialog && restoreFile != null) {
        RestoreConfirmDialog(
            file = restoreFile!!,
            sourceLabel = "主题",
            sourceName = restoreName,
            entryCount = restoreEntryCount,
            fileRepository = fileRepository,
            restoreNote = Microcopy.TOPIC_RESTORE_NOTE,
            onDismiss = {
                showRestoreDialog = false
                restoreFile = null
                restoreEntryCount = 0
            },
            onConfirmed = {
                showRestoreDialog = false
                restoreFile = null
                restoreEntryCount = 0
                reloadTopics()
            }
        )
    }

    // 页内新建·命名框（§15.1 新建流程：复用移入流程 NewTopicNameDialog 零改动——形态/聚焦/
    // 「创建」按钮原样；校验链＋创建一体经 createEmptyTopic 原子复用，各失败分支 toast 且框保持）
    if (createStep == TopicCreateStep.NAMING) {
        NewTopicNameDialog(
            name = newTopicInput,
            onNameChange = { newTopicInput = it },
            onCreate = {
                coroutineScope.launch {
                    val created = withContext(Dispatchers.IO) {
                        topicRepository.createEmptyTopic(newTopicInput)
                    }
                    when (created) {
                        is TopicCreateResult.Created -> {
                            // 成功：关框＋重载＋toast（§15.1）
                            createStep = null
                            newTopicInput = ""
                            showToast(context, Microcopy.TOAST_TOPIC_CREATED)
                            reloadTopics()
                        }
                        is TopicCreateResult.NameExists -> {
                            // 同名 → C5 提示分支（提示非指路，94(b)）
                            sameNameExistingName = created.existing.displayName
                            createStep = TopicCreateStep.SAME_NAME_PROMPT
                        }
                        TopicCreateResult.NameEmpty -> showToast(context, Microcopy.TOAST_TOPIC_NAME_EMPTY)
                        TopicCreateResult.NameReserved -> showToast(context, Microcopy.TOAST_TOPIC_NAME_RESERVED)
                        TopicCreateResult.NameIllegalChars -> showToast(context, Microcopy.TOAST_TOPIC_NAME_ILLEGAL)
                        TopicCreateResult.NameTooLong -> showToast(context, Microcopy.TOAST_TOPIC_NAME_TOO_LONG)
                        // 创建失败：框保持、输入保留可重试（§15.1）
                        TopicCreateResult.WriteFailed -> showToast(context, Microcopy.TOAST_TOPIC_CREATE_FAILED)
                    }
                }
            },
            onDismiss = { createStep = null } // 「取消」/点外 → 关流程
        )
    }

    // 页内新建·同名提示（§15.1 C5 定稿；2026-11-11 换机验证裁定：去单「知道了」按钮——点外/系统返回
    // 均回命名框（输入保留，可改名），沿 TopicSelectDialog 无按钮先例；无「移入该主题」按钮
    //（主题页语境无移入可言，94(b) 断言）
    if (createStep == TopicCreateStep.SAME_NAME_PROMPT) {
        AlertDialog(
            onDismissRequest = { createStep = TopicCreateStep.NAMING },
            title = { Text(Microcopy.DLG_SAME_NAME_TITLE, fontWeight = FontWeight.SemiBold) },
            text = { Text(Microcopy.dlgTopicPageSameNameBody(sameNameExistingName)) },
            confirmButton = {}
        )
    }
}

/**
 * 主题卡（T-14，§15.1 TopicCard 行）：[SwipeCard] 薄包装（A8 裁定，同 ArchiveCard 形态）＋
 * 内容行——40dp 彩色圆形首字标（容器色＝[badgePalette] 按全名散列取色、字＝[firstGrapheme]
 * 用户可感知首字、文字色 [contentColorFor] 黑白自适应）＋主题名（titleMedium Medium）＋
 * 小字副文案（onSurfaceVariant）。
 */
@Composable
private fun TopicCard(
    topic: TopicDocument,
    badgePalette: List<Color>,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
    onSwipeLeftRestore: () -> Unit
) {
    // 取色链（§15.1 首字标规则）：deriveBadgePalette(primary, surface)[badgeIndex(displayName)]
    val badgeColor = badgePalette[badgeIndex(topic.displayName)]
    SwipeCard(
        // 85(a)：非空主题右滑删除钳 0 零响应（swipeToDeleteEnabled=false）；仅空主题（0 条）可右滑删除
        swipeToDeleteEnabled = topic.entryCount == 0,
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(badgeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstGrapheme(topic.displayName),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColorFor(badgeColor)
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.displayName,
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
