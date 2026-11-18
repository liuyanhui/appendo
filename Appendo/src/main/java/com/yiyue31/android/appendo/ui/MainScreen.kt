package com.yiyue31.android.appendo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.TopicCreateResult
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.data.TopicRepository
import androidx.core.content.ContextCompat
import com.yiyue31.android.appendo.ui.EntryListScreen
import com.yiyue31.android.appendo.ui.showToast
import com.yiyue31.android.appendo.ui.theme.AppendoDarkScheme
import com.yiyue31.android.appendo.ui.theme.AppendoLightScheme
import com.yiyue31.android.appendo.ui.theme.ThemeMode
import com.yiyue31.android.appendo.util.CalendarEntryMapper
import com.yiyue31.android.appendo.util.CalendarLauncher
import com.yiyue31.android.appendo.util.DuplicateHintThrottle
import com.yiyue31.android.appendo.util.EntryKey
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import com.yiyue31.android.appendo.util.MarkdownFormatter
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import com.yiyue31.android.appendo.util.RefreshGuard
import com.yiyue31.android.appendo.util.SafMarkdownFile
import com.yiyue31.android.appendo.util.MoveResult
import com.yiyue31.android.appendo.util.TopicMoveTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Constants for magic numbers
// 振动时长常量与 performVibration 已随 SwipeCard 骨架共享下沉（T-13，A-4/E-3），本文件直接引用
private const val FILE_CHANGE_POLL_INTERVAL_MS = 2000L

// parseMarkdownEntries 委托给 EntryParser（v1.1 收敛）；保留至 T-016 迁移现有调用方后删除。
fun parseMarkdownEntries(content: String): List<LinkEntry> =
    EntryParser.parse(content).map { LinkEntry.from(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    fileRepository: FileRepository,
    onNavigateToArchiveList: () -> Unit = {},
    onNavigateToTopics: () -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChange: (ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var useSAF by remember { mutableStateOf(fileRepository.isUsingSAF()) }
    var fileUri by remember { mutableStateOf(fileRepository.getFileUri()) }
    var defaultFile by remember { mutableStateOf(fileRepository.getDefaultFile()) }
    var entryCount by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf(emptyList<LinkEntry>()) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var inputContent by remember { mutableStateOf("") }
    var lastModified by remember { mutableStateOf(fileRepository.getFileLastModified()) }
    var showMenu by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showSetupGuideDialog by remember { mutableStateOf(false) }
    // SAF 授权失效（TD-021）：读失败且 SAF 模式时弹此对话框，不再静默空列表
    var showSafInvalidDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<LinkEntry?>(null) }
    var editContent by remember { mutableStateOf("") }
    /** 编辑草稿归属条目（关窗不丢字：同条目重开沿用草稿、换条目重置为原文；保存成功两者皆清） */
    var lastEditedTs by remember { mutableStateOf<String?>(null) }

    // 「移入主题」多选态（topic-move 设计 §7.2）：选中集按 EntryKey 维护（§7.3，与复合 key 解耦）
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<EntryKey>()) }

    /** 退出多选并清空选中集（§7.2：关闭 × / 系统返回 / SAF 失效回退共用） */
    fun exitSelectionMode() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    // —— 「移入主题」流程（WA-1 迁 MoveFlowHolder：三动词单一状态机，工作区同款）——
    // 宿主自持：目标列表快照/同名指路对象/长按预览条目（Holder 持流程态与命名草稿）
    val moveFlow = remember { MoveFlowHolder() }
    var moveTopics by remember { mutableStateOf(emptyList<TopicDocument>()) }
    var sameNameExisting by remember { mutableStateOf<TopicDocument?>(null) }
    // 多选态长按预览（§7.4）：持有预览条目，对话框本体＝TopicMoveDialogs.EntryPreviewDialog
    var previewEntry by remember { mutableStateOf<LinkEntry?>(null) }

    val topicRepository = remember { TopicRepository(context) }

    /**
     * 关闭移入流程全部对话框（§8.1「取消」路径：**保持多选态与选中集**——用户可换目标重来；
     * 本流程新建的空主题保留，可删可复用，§6.5）。Holder.close() 保留命名草稿（修订⑤）。
     */
    fun closeMoveFlow() {
        moveFlow.close()
        sameNameExisting = null
    }

    /** 打开对话框 1：主题列表 createdAt 倒序；每主题实时读文件计数，加载下放 [Dispatchers.IO]。 */
    fun openMoveFlow() {
        coroutineScope.launch {
            moveTopics = withContext(Dispatchers.IO) { topicRepository.listTopics() }
            moveFlow.start(MoveVerb.MOVE_IN, MoveFlowStep.SELECT_TARGET)
        }
    }

    val entryListState = rememberLazyListState()

    // Helper function to get current MarkdownFileOperations instance
    fun getCurrentMarkdownFile(): MarkdownFileOperations {
        return MarkdownFileFactory.create(context, useSAF, fileUri, defaultFile)
    }

    // Helper function to refresh entry count and entries from a single file read
    fun refreshEntryCount() {
        coroutineScope.launch {
            try {
                // 文件 I/O + 解析下放 Dispatchers.IO（中-7：避免主线程 ANR，尤其 2s 轮询路径）
                // Triple(条目列表或 null[跳过], recovered, SAF 读失败[TD-021])
                val (newEntries, recovered, safReadFailed) = withContext(Dispatchers.IO) {
                    val mdFile = getCurrentMarkdownFile()
                    if (!mdFile.exists()) {
                        mdFile.initHeader()
                    }
                    val result = mdFile.readAllWithStatus()
                    val parsed = parseMarkdownEntries(result.content)
                    // 刷新守卫（TD-012/016，决策收敛于 RefreshGuard 纯函数）：
                    // 读失败/空白内容 → 跳过刷新与对账（防误杀提醒）；header-only 合法空 → 放行（防删至 0 条 UI 滞留）
                    if (RefreshGuard.shouldSkipRefresh(result.failed, result.content, parsed.size, entries.size)) {
                        Triple<List<LinkEntry>?, Boolean, Boolean>(null, false, result.failed && useSAF)
                    } else {
                        Triple(parsed, result.recovered, false)
                    }
                }
                if (newEntries == null) {
                    // TD-021：SAF 授权丢失（覆盖安装等）→ 弹窗引导，不再静默空列表
                    if (safReadFailed) {
                        // §7.2：SAF 失效模态引导优先——自动退出多选并清空选中集（源不可读时选择上下文已无意义）
                        // T-06：移入流程对话框若在途一并关闭（同因：源不可读，移入事务失去前提）
                        closeMoveFlow()
                        exitSelectionMode()
                        showSafInvalidDialog = true
                    }
                    return@launch
                }
                if (recovered) {
                    // SAF 软恢复发生时透明提示（specs 46）；默认模式 recovered 恒 false
                    showToast(context, "检测到上次异常退出，已从备份恢复，请检查近期改动")
                }
                entries = newEntries
                entryCount = newEntries.size
                // §7.3：刷新后保留仍存在的选中项——被删剔除、新插入默认未选（勾选按 key 查 Set，不错位）
                selectedKeys = retainedSelection(selectedKeys, newEntries)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("MainScreen", "Failed to refresh entries", e)
                }
                entryCount = 0
                entries = emptyList()
            }
        }
    }

    // —— 「移入主题」执行（T-06，§8.1 对话框 3 确认/重试共用入口；§6.6/§8.2 处置）——
    /**
     * 执行移入组合事务（失败重试同走本入口，move 幂等收敛，84(b)）：
     * - 事务在 [Dispatchers.IO]（200 条＝2 次全文件写，不 ANR，89(p)）；
     * - 成功分支仅首次指路旗标消费（提醒联动随自建提醒退役整体删除——R 批次，specs 16.C）。
     * - 成功：关全部对话框＋退出多选＋toast 三态＋setFileLastModified＋
     *   refreshEntryCount（§6.6 立即主动刷新，不等 2 秒轮询）。
     * - 失败：toast 按分类、对话框 3 保持变「重试」、保持勾选与目标；仅 SourceDeleteFailed
     *   例外同时刷新（源可能已部分移除，即刻如实反映，§6.6），其余失败态源未动不刷新。
     */
    fun executeMove() {
        val target = moveFlow.transferTarget ?: return
        val keys = selectedKeys
        if (keys.isEmpty()) {
            // 防御：确认停留期间选中集被清空（轮询剔除/SAF 失效退出）——无条目可移，关流程保多选语义
            closeMoveFlow()
            return
        }
        val source = getCurrentMarkdownFile()
        val targetMd = FileBasedMarkdownFile(context, target.file)
        moveFlow.markExecuting(true)
        coroutineScope.launch {
            var firstMoveGuide = false
            val result = withContext(Dispatchers.IO) {
                val r = TopicMoveTransaction.move(source, targetMd, keys)
                if (r is MoveResult.Success) {
                    // 首次指路（§15.4 U1）：仅 moved>0 的 Success 消耗旗标（N=0 不消耗不展示）——
                    // 旗标判定归宿主（WA-1：经 Mapping.guideSuffix 拼装，防迁移丢失）
                    if (r.moved > 0) {
                        firstMoveGuide = topicRepository.consumeFirstMoveGuide()
                    }
                }
                r
            }
            moveFlow.markExecuting(false)
            // 产出映射（WA-1：全动词统一经 MoveOutcomeMapping——移入组合随 Holder 迁入）
            val outcome = MoveOutcomeMapping.map(
                result = result,
                verb = MoveVerb.MOVE_IN,
                useSAF = useSAF,
                sourceName = "主列表",
                targetName = target.displayName,
                guideSuffix = if (firstMoveGuide) Microcopy.firstMoveGuideSuffix else null
            )
            when (result) {
                is MoveResult.Success -> {
                    closeMoveFlow()
                    exitSelectionMode()
                }
                is MoveResult.SameDocument -> Unit // 防御（96）：只提示，不动选择上下文
                else -> if (outcome.keepRetryState) moveFlow.markFailed() // 对话框 3 保持变「重试」（84(b)）
            }
            if (outcome.refreshMain) {
                // §6.6：主文件条目增减→立即主动刷新（mtime 双保险，不等 2 秒轮询）；
                // 移入 SourceDeleteFailed 例外同理（源可能已部分移除，即刻如实反映）
                fileRepository.setFileLastModified(System.currentTimeMillis())
                refreshEntryCount()
            }
            showToast(context, outcome.toast ?: return@launch)
        }
    }

    /** 归档后重同步三状态（A-2 onComplete 等价动作）。 */
    fun resyncArchiveState() {
        useSAF = fileRepository.isUsingSAF()
        fileUri = fileRepository.getFileUri()
        defaultFile = fileRepository.getDefaultFile()
    }

    /**
     * 归档二选一执行编排（修订二 §15.3：两路径表逐行实现）。
     * 编排层只执行 [archiveActionFor] 的决定；归档/清空各以 runCatching 兜底（E-1），
     * 全程 IO（A-9：修正旧清空框主线程文件 IO）；TD-017：Failed 不得清空。
     */
    fun runArchiveAction(path: ArchivePath) {
        val mdFile = getCurrentMarkdownFile()
        runArchive(
            context = context,
            scope = coroutineScope,
            sourceMd = mdFile,
            getTarget = { fileRepository.getArchiveFile() },
            wordbook = mainArchiveWordbook(),
            path = path,
            onCompleted = { shouldClear, _ ->
                // A-2 onComplete 等价动作：两路径共用重同步三状态；归档并清空另加
                // clearFileLastModified＋refreshEntryCount（主文件 mtime 键仅主界面触碰）
                resyncArchiveState()
                if (shouldClear) {
                    fileRepository.clearFileLastModified()
                    refreshEntryCount()
                }
            }
        )
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

        // 应用启动即重排提醒（TD-014：覆盖安装/force-stop 会丢闹钟，不能只等开机广播）

        // 旧数据一次性隔离迁移（2026-09-01 决策③）：为未隔离的边界形态内容行补 ZWSP，旧条目不再丢行。
        // 幂等（无需迁移返回 null）；走 writeAll 原子写；完成后靠 mtime 轮询自然刷新 UI。
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val mdFile = getCurrentMarkdownFile()
                if (mdFile.exists()) {
                    EntryParser.migrateLegacyIsolation(mdFile.readAll())?.let { migrated ->
                        if (mdFile.writeAll(migrated)) {
                            fileRepository.setFileLastModified(System.currentTimeMillis())
                        }
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("MainScreen", "Legacy isolation migration failed", e)
                }
            }
        }

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

    // §7.2：系统返回手势退出多选并清空选中集（仅多选态拦截）
    BackHandler(enabled = selectionMode) { exitSelectionMode() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                // 多选态：顶栏整体切换为 contextual bar（§7.2）——浏览态主标题/副标题由此隐藏
                MultiSelectTopBar(
                    selectedCount = selectedKeys.size,
                    onExit = { exitSelectionMode() },
                    onMoveIn = { openMoveFlow() } // T-06：打开对话框 1 目标选择（§8.1）
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Appendo",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "已收集 $entryCount 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        // v1.4 视觉裁定：顶栏随页面底色（colorScheme.background），与内容区无缝
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        // 主题页入口（修订二 §15.1，90）：浏览态常驻（主文件 0 条仍显示，此时排列退化为
                        // [主题][菜单]）；多选态随 contextual bar 整体隐藏（本顶栏整体替换，P3-13），
                        // 退出多选即恢复。排列 [主题][多选][菜单]——浏览导航类动作靠左、工具靠右；
                        // contentDescription 直写沿本文件既有惯例（「多选」「菜单」同款）
                        IconButton(onClick = onNavigateToTopics) {
                            // AutoMirrored 版（方向敏感图标 RTL 镜像；与本文件 KeyboardArrowLeft 同款先例）
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "主题")
                        }
                        // 多选入口（§7.1 方案 A）：浏览态常驻，条目数为 0 时隐藏（空列表无从选择）。
                        // contentDescription 直写沿本文件既有惯例（「菜单」同款），字面=§9.2 定稿
                        if (entryCount > 0) {
                            IconButton(onClick = { selectionMode = true }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "多选")
                            }
                        }
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
                            // 「归档」菜单项已移除（修订二 93：入口唯一化到 2x2 网格「归档」二选一）
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
                                text = { Text("外观") },
                                onClick = {
                                    showMenu = false
                                    showThemePicker = true
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // §7.2：多选态隐藏 2x2 按钮网格（整屏只服务选择，P1）；退出多选即恢复
            if (!selectionMode) {
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
                            containerColor = MaterialTheme.colorScheme.primary
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
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
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
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("分享全部", fontWeight = FontWeight.Medium)
                    }

                    // 第四格「归档」（修订二 93：清空/归档合并，点击触发二选一；旧长按清空退役）
                    OutlinedButton(
                        onClick = {
                            if (entries.isEmpty()) {
                                // 触发守卫（§15.3 ②）：0 条不开框，消除旧路径双 toast 冗余
                                showToast(context, Microcopy.TOAST_NOTHING_TO_ARCHIVE)
                            } else {
                                showArchiveDialog = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        // 2026-11-05 用户裁定：颜色回 error 红（沿旧「清空」格视觉；78 语义上红＝注意，
                        // 与二选一含清空路径相符——设计修订二 A6 中性色裁定反转）
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(Microcopy.BTN_CELL_ARCHIVE, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

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
                    selectionMode = selectionMode,
                    selectedKeys = selectedKeys,
                    onToggleSelection = { entry ->
                        // §7.3：按 key 增删选中集（与 LazyColumn 复合 key 解耦）
                        val key = entry.entryKey
                        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                    },
                    onPreview = { entry ->
                        // T-06（§7.4，82 修订 b）：多选态长按预览——持有条目，弹 EntryPreviewDialog
                        previewEntry = entry
                    },
                    onEntryLongClick = { content ->
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("entry", content))
                        showToast(context, Microcopy.TOAST_COPIED)
                    },
                    onEntrySwipeToDelete = { timestamp ->
                        entryToDeleteTimestamp = timestamp
                        showDeleteDialog = true
                    },
                    onEntryClick = { entry ->
                        // 关窗不丢字（2026-11-11）：同一条目重开沿用未保存草稿（仅换条目时重置为原文）
                        if (lastEditedTs != entry.timestamp) {
                            editContent = entry.content
                        }
                        lastEditedTs = entry.timestamp
                        selectedEntry = entry
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
                                color = MaterialTheme.colorScheme.error
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
                                Text("删除", color = MaterialTheme.colorScheme.error)
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
                    color = MaterialTheme.colorScheme.primary
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
                    Text("打开已有文件", color = MaterialTheme.colorScheme.primary)
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

    // 主题选择（需求 48）：选中即生效并持久化，无需二次确认
    if (showThemePicker) {
        ThemePickerDialog(
            current = currentThemeMode,
            onPick = { mode ->
                onThemeChange(mode)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false }
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
                        color = MaterialTheme.colorScheme.primary
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
            // 2026-11-11 换机验证裁定：纯信息弹窗去单「关闭」按钮——点框外/系统返回双通道可关
            //（沿 TopicSelectDialog 无按钮先例）；confirmButton 为必填槽传空
            confirmButton = {}
        )
    }

    // 归档清空二选一对话框（WB-1：本体迁共享 ArchiveChoiceDialog）
    if (showArchiveDialog) {
        ArchiveChoiceDialog(
            entryCount = entryCount,
            onArchiveAndClear = {
                showArchiveDialog = false
                runArchiveAction(ArchivePath.ARCHIVE_AND_CLEAR)
            },
            onArchiveOnly = {
                showArchiveDialog = false
                runArchiveAction(ArchivePath.ARCHIVE_ONLY)
            },
            onDismiss = { showArchiveDialog = false }
        )
    }

    // Detail/edit dialog（WA-2：对话框本体迁共享组件 EntryEditDialog——草稿/时序宿主持有）
    if (showDetailDialog && selectedEntry != null) {
        /**
         * 编辑保存核心（宿主单一保存路径，§2.3 消重——保存/日历两按钮共用）：
         * 空→toast 保持开框；未变→关框清草稿；写成功→toast＋刷新＋关框清草稿；
         * **写失败→toast 保持开框不清草稿**（WA-2 行为修正 1：原失败仍关框清草稿——修订⑤
         * 「保存成功才清空」精神）。返回值＝是否已落盘可继续后续（日历跳转）。
         */
        fun performEditSaveCore(): Boolean {
            if (editContent.isBlank()) {
                showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                return false
            }
            val entry = selectedEntry ?: return false
            if (editContent == entry.content) {
                showDetailDialog = false
                selectedEntry = null
                editContent = ""
                lastEditedTs = null
                return true
            }
            return try {
                val mdFile = getCurrentMarkdownFile()
                if (mdFile.updateEntry(entry.timestamp, editContent)) {
                    fileRepository.setFileLastModified(System.currentTimeMillis())
                    refreshEntryCount()
                    showToast(context, Microcopy.TOAST_SAVED)
                    true
                } else {
                    showToast(context, Microcopy.TOAST_SAVE_FAILED)
                    false
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("MainScreen", "Failed to update entry", e)
                }
                showToast(context, Microcopy.TOAST_SAVE_FAILED)
                false
            }
        }

        fun closeEditDialog() {
            showDetailDialog = false
            selectedEntry = null
        }

        EntryEditDialog(
            entry = selectedEntry!!,
            draft = editContent,
            onDraftChange = { editContent = it },
            onSave = {
                if (performEditSaveCore()) {
                    closeEditDialog()
                    editContent = ""
                    lastEditedTs = null
                } // 失败保持开框（行为修正 1）——草稿保留可改后重存
            },
            onRequestCalendar = {
                // 需求 75 先落盘：空校验→有修改先保存（失败 return 保持开框）→清草稿关框跳日历
                if (!performEditSaveCore()) return@EntryEditDialog
                val entry = CalendarEntryMapper.map(editContent.ifBlank { selectedEntry?.content ?: "" })
                closeEditDialog()
                editContent = ""
                lastEditedTs = null
                keyboardController?.hide()
                if (!CalendarLauncher.launch(context, entry)) {
                    showToast(context, Microcopy.TOAST_NO_CALENDAR)
                }
            },
            onDismiss = { closeEditDialog() } // 点外/取消/返回三通道：仅关窗不清草稿（修订⑤）
        )
    }

    // SAF 授权失效引导（TD-021）：数据仍在原文件中，绝不静默空列表
    if (showSafInvalidDialog) {
        AlertDialog(
            onDismissRequest = { showSafInvalidDialog = false },
            title = {
                Text("数据文件访问已失效", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text("自定义目录的文件授权在应用更新后可能失效（本机系统行为），因此暂时读不到数据。\n\n你的数据仍在原文件中，不会丢失。可重选同一文件恢复访问，或回退到应用默认文件。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSafInvalidDialog = false
                    changeFileLauncher.launch("Appendo.md") // 重选原文件恢复授权
                }) {
                    Text("重选文件", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSafInvalidDialog = false
                    fileRepository.clearFileUri()
                    useSAF = false
                    fileUri = null
                    defaultFile = fileRepository.getDefaultFile()
                    fileRepository.clearFileLastModified()
                    refreshEntryCount()
                }) {
                    Text("回退默认", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // Manual input dialog（WA-2：对话框本体迁共享组件 EntryInputDialog——草稿宿主持有）
    if (showInputDialog) {
        /**
         * 追加保存核心（保存路径——查重＋节流旁注经共享 helper [chooseAppendToast]，§2.3）：
         * 空→toast 保持开框；成功→toast（含重复旁注）＋刷新；**失败→toast 保持开框**（行为修正 1）。
         */
        fun performAppendSave(): Boolean {
            if (inputContent.isBlank()) {
                showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                return false
            }
            return try {
                val mdFile = getCurrentMarkdownFile()
                // 追加前查重（按内容，忽略时间戳）
                val existingSameCount = parseMarkdownEntries(mdFile.readAll())
                    .count { it.content == inputContent }
                if (mdFile.append(inputContent)) {
                    fileRepository.setFileLastModified(System.currentTimeMillis())
                    refreshEntryCount()
                    showToast(context, chooseAppendToast(inputContent, existingSameCount))
                    true
                } else {
                    showToast(context, Microcopy.TOAST_APPEND_FAILED)
                    false
                }
            } catch (_: Exception) {
                showToast(context, Microcopy.TOAST_APPEND_FAILED)
                false
            }
        }

        /** 日历路径核心（75 先落盘）：追加成功 plain toast（**不带查重旁注**——沿现状）；
         *  失败保持开框。返回新落盘内容供日历预填（未变场景不适用——输入恒有新内容）。 */
        fun performAppendForCalendar(): String? {
            if (inputContent.isBlank()) {
                showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                return null
            }
            return try {
                val mdFile = getCurrentMarkdownFile()
                if (mdFile.appendReturningTimestamp(inputContent) != null) {
                    fileRepository.setFileLastModified(System.currentTimeMillis())
                    refreshEntryCount()
                    showToast(context, Microcopy.TOAST_APPENDED)
                    inputContent
                } else {
                    showToast(context, Microcopy.TOAST_APPEND_FAILED)
                    null
                }
            } catch (_: Exception) {
                showToast(context, Microcopy.TOAST_APPEND_FAILED)
                null
            }
        }

        EntryInputDialog(
            draft = inputContent,
            onDraftChange = { inputContent = it },
            onAppend = {
                if (performAppendSave()) {
                    inputContent = ""
                    showInputDialog = false
                } // 失败保持开框（行为修正 1）——草稿保留
            },
            onRequestCalendar = {
                val appended = performAppendForCalendar() ?: return@EntryInputDialog
                val entry = CalendarEntryMapper.map(appended)
                inputContent = ""
                showInputDialog = false
                if (!CalendarLauncher.launch(context, entry)) {
                    showToast(context, Microcopy.TOAST_NO_CALENDAR)
                }
            },
            onDismiss = { showInputDialog = false } // 仅关窗，草稿保留（修订⑤；键盘组件内收起）
        )
    }

    // —— 「移入主题」流程对话框栈（T-06，§8.1；对话框本体在 TopicMoveDialogs.kt）——
    when (moveFlow.step) {
        MoveFlowStep.SELECT_TARGET -> TopicSelectDialog(
            topics = moveTopics, // createdAt 倒序（TopicRepository.listTopics 序）
            onSelect = { topic ->
                // 点选已有主题 → 对话框 3（§8.1）
                moveFlow.selectTarget(topic)
                moveFlow.clearFailed()
                moveFlow.goTo(MoveFlowStep.CONFIRM)
            },
            onNewTopic = { moveFlow.goTo(MoveFlowStep.NEW_TOPIC) },
            // 点外/返回关全部——保持多选态与选中集（与对话框 3「取消」同处置）
            onDismiss = ::closeMoveFlow
        )
        MoveFlowStep.NEW_TOPIC -> NewTopicNameDialog(
            name = moveFlow.newTopicInput,
            onNameChange = { moveFlow.newTopicInput = it },
            onCreate = {
                // 校验链＋创建一体经 createEmptyTopic（原子，无「先校验后创建」间隙）；
                // 校验各失败分支 → 对应 toast 且对话框保持（§8.1）；取消留空文档不回收（§6.5）
                coroutineScope.launch {
                    val created = withContext(Dispatchers.IO) {
                        topicRepository.createEmptyTopic(moveFlow.newTopicInput)
                    }
                    when (created) {
                        is TopicCreateResult.Created -> {
                            // 进确认框前已建空文档（§6.5 创建时机取舍）：确认框取消留空文档，可删可复用
                            moveFlow.selectTarget(TopicDocument(
                                file = created.file,
                                displayName = moveFlow.newTopicInput.trim(),
                                createdAt = System.currentTimeMillis(),
                                entryCount = 0
                            ))
                            moveFlow.clearFailed()
                            moveFlow.newTopicInput = "" // 名称已消费进目标（关窗不丢字口径：此处清）
                            moveFlow.goTo(MoveFlowStep.CONFIRM)
                        }
                        is TopicCreateResult.NameExists -> {
                            // 同名 → 对话框 2b 指路（83 修订 c）
                            sameNameExisting = created.existing
                            moveFlow.goTo(MoveFlowStep.SAME_NAME)
                        }
                        TopicCreateResult.NameEmpty -> showToast(context, Microcopy.TOAST_TOPIC_NAME_EMPTY)
                        TopicCreateResult.NameReserved -> showToast(context, Microcopy.TOAST_TOPIC_NAME_RESERVED)
                        TopicCreateResult.NameIllegalChars -> showToast(context, Microcopy.TOAST_TOPIC_NAME_ILLEGAL)
                        TopicCreateResult.NameTooLong -> showToast(context, Microcopy.TOAST_TOPIC_NAME_TOO_LONG)
                        TopicCreateResult.WriteFailed -> {
                            // 创建失败 → toast 中止流程（§8.1）；多选态与选中集保持
                            showToast(context, Microcopy.toastMoveFailedCreateTopic())
                            closeMoveFlow()
                        }
                    }
                }
            },
            onDismiss = { moveFlow.goTo(MoveFlowStep.SELECT_TARGET) } // 「取消」→ 回对话框 1（§8.1）
        )
        MoveFlowStep.SAME_NAME -> SameNameDialog(
            existingName = sameNameExisting?.displayName ?: "",
            onMoveToExisting = {
                // 「移入该主题」→ 目标切该已有主题，直达对话框 3（§8.1）
                moveFlow.selectTarget(sameNameExisting)
                sameNameExisting = null
                moveFlow.clearFailed()
                moveFlow.goTo(MoveFlowStep.CONFIRM)
            },
            onDismiss = { moveFlow.goTo(MoveFlowStep.NEW_TOPIC) } // 「取消」→ 回对话框 2，输入保留（§8.1）
        )
        MoveFlowStep.CONFIRM -> MoveConfirmDialog(
            count = selectedKeys.size, // N＝所选条数（84(d)/88 口径）
            topicName = moveFlow.transferTarget?.displayName ?: "",
            executing = moveFlow.executing,
            failed = moveFlow.failed,
            onConfirm = { executeMove() }, // 失败重试同入口（move 幂等收敛）
            onDismiss = ::closeMoveFlow // 「取消」关全部对话框，保持多选态与选中集（§8.1）
        )
        null -> {}
    }

    // 多选态长按预览对话框（§7.4，82 修订 b；本体 TopicMoveDialogs.EntryPreviewDialog）
    previewEntry?.let { entry ->
        EntryPreviewDialog(entry = entry, onDismiss = { previewEntry = null })
    }
}


/** 主题三选对话框（需求 48）：跟随系统整行 + 浅色/深色并列（真机验收两轮修正：纯横排四字换行、纯纵排面板偏高）。 */
@Composable
private fun ThemePickerDialog(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外观", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                // 首行整宽：四字标签独占一行不换行；次行两个二字标签并列（真机两轮验收定稿）
                // 预览样式：浅色/深色按钮用对应主题真实配色（引用全局 scheme，不写死），
                // 跟随系统用当前生效主题（=系统现在的样子）；选中态=加粗主色描边（不覆盖预览底色）
                ThemeOptionButton(
                    label = "跟随系统",
                    selected = current == ThemeMode.SYSTEM,
                    previewScheme = MaterialTheme.colorScheme,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPick(ThemeMode.SYSTEM) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeOptionButton(
                        label = "浅色",
                        selected = current == ThemeMode.LIGHT,
                        previewScheme = AppendoLightScheme,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(ThemeMode.LIGHT) }
                    )
                    ThemeOptionButton(
                        label = "深色",
                        selected = current == ThemeMode.DARK,
                        previewScheme = AppendoDarkScheme,
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(ThemeMode.DARK) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 主题选项按钮：以 [previewScheme] 的 surface/onSurface 预览该主题观感
 * （颜色引用全局主题定义，不在按钮内写死）；选中态=加粗主色描边，不覆盖预览底色。
 */
@Composable
private fun ThemeOptionButton(
    label: String,
    selected: Boolean,
    previewScheme: ColorScheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else previewScheme.outline.copy(alpha = 0.4f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = previewScheme.surface,
            contentColor = previewScheme.onSurface
        )
    ) {
        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

internal fun copyContent(context: android.content.Context, mdFile: MarkdownFileOperations) {
    try {
        // 出口格式化（v1.3，specs 68~70）：经 parse 产物构建，无结构标记、无 ZWSP、秒级时间戳
        val entries = EntryParser.parse(mdFile.readAll())
        if (entries.isEmpty()) {
            showToast(context, "暂无内容可复制")
            return
        }
        val content = EntryParser.formatForExport(entries)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("appendo", content))
        showToast(context, Microcopy.TOAST_COPIED_ALL)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("MainScreen", "Copy failed", e)
        }
        showToast(context, "复制失败")
    }
}

internal fun shareContent(context: android.content.Context, mdFile: MarkdownFileOperations) {
    try {
        // 出口格式化（v1.3，specs 68~70）：经 parse 产物构建，无结构标记、无 ZWSP、秒级时间戳
        val entries = EntryParser.parse(mdFile.readAll())
        if (entries.isEmpty()) {
            showToast(context, "暂无内容可分享")
            return
        }
        val content = EntryParser.formatForExport(entries)
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
