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
import com.yiyue31.android.appendo.reminder.AlarmScheduler
import com.yiyue31.android.appendo.reminder.ReminderStore
import com.yiyue31.android.appendo.reminder.rescheduleAll
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.yiyue31.android.appendo.reminder.ReminderIntents
import com.yiyue31.android.appendo.util.Recurrence
import com.yiyue31.android.appendo.util.ReminderMeta
import com.yiyue31.android.appendo.util.ReminderLogic
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
    scrollToTs: String? = null,
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
    var showSelfCheck by remember { mutableStateOf(false) }
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

    // 「移入主题」多选态（topic-move 设计 §7.2）：选中集按 EntryKey 维护（§7.3，与复合 key 解耦）
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<EntryKey>()) }

    /** 退出多选并清空选中集（§7.2：关闭 × / 系统返回 / SAF 失效回退共用） */
    fun exitSelectionMode() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    // —— 「移入主题」流程（T-06，§8.1）：对话框栈状态（1 目标选择 / 2 新建命名 / 2b 同名指路 / 3 确认）——
    var moveFlowStep by remember { mutableStateOf<MoveFlowStep?>(null) }
    var moveTopics by remember { mutableStateOf(emptyList<TopicDocument>()) }
    var moveTarget by remember { mutableStateOf<TopicDocument?>(null) }
    var sameNameExisting by remember { mutableStateOf<TopicDocument?>(null) }
    // 对话框 2 输入由本处持有：2b「取消」返回对话框 2 时输入保留（§8.1）
    var newTopicInput by remember { mutableStateOf("") }
    // 对话框 3 执行/失败态（§8.1）：执行中按钮禁用＋「移入中…」；失败对话框保持变「重试」
    var moveExecuting by remember { mutableStateOf(false) }
    var moveFailed by remember { mutableStateOf(false) }
    // 多选态长按预览（§7.4）：持有预览条目，对话框本体＝TopicMoveDialogs.EntryPreviewDialog
    var previewEntry by remember { mutableStateOf<LinkEntry?>(null) }

    val topicRepository = remember { TopicRepository(context) }

    /**
     * 关闭移入流程全部对话框（§8.1「取消」路径：**保持多选态与选中集**——用户可换目标重来；
     * 本流程新建的空主题保留，可删可复用，§6.5 创建时机取舍）。
     */
    fun closeMoveFlow() {
        moveFlowStep = null
        moveTarget = null
        sameNameExisting = null
        newTopicInput = ""
        moveExecuting = false
        moveFailed = false
    }

    /** 打开对话框 1：主题列表 createdAt 倒序；每主题实时读文件计数，加载下放 [Dispatchers.IO]。 */
    fun openMoveFlow() {
        coroutineScope.launch {
            moveTopics = withContext(Dispatchers.IO) { topicRepository.listTopics() }
            moveFlowStep = MoveFlowStep.SELECT_TARGET
        }
    }

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
    var pendingSchedule by remember { mutableStateOf<Triple<String, Long, Recurrence>?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val p = pendingSchedule
        pendingSchedule = null
        if (p != null) {
            if (granted) scheduleReminder(context, p.first, p.second, p.third)
            else showToast(context, "未授予通知权限，提醒将不会显示")
        }
    }

    fun startSetReminder(ts: String, triggerAt: Long, recurrence: Recurrence) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingSchedule = Triple(ts, triggerAt, recurrence)
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleReminder(context, ts, triggerAt, recurrence)
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

    // —— 「移入主题」执行（T-06，§8.1 对话框 3 确认/重试共用入口；§6.6/§8.2 处置）——
    /**
     * 执行移入组合事务（失败重试同走本入口，move 幂等收敛，84(b)）：
     * - 事务与提醒联动的源重读均在 [Dispatchers.IO]（200 条＝2 次全文件写，不 ANR，89(p)）；
     * - 提醒联动严格 §8.2 执行序：① 仅 Success（含 N=0）取消——一切失败态（含 SourceDeleteFailed）
     *   均不取消；② K 在成功回调内以 sidecar 时点快照重算（确认框 K 仅预览）；③ 同 ts 连坐防护
     *   ——以移入后的源重读解析为准，同 ts 仍有条目留在源则不取消该提醒；④ 取消动作与删除条目
     *   联动同款（AlarmScheduler.cancel ＋ ReminderStore.remove 成对）；⑤ K>0 时 toast 追加附注；
     *   ⑥ 恢复不复活＝零代码保证（纯条目追加无复活路径，README 声明落 T-11/R1）。
     * - 成功：关全部对话框＋退出多选＋toast 三态（K>0 追加提醒附注）＋setFileLastModified＋
     *   refreshEntryCount（§6.6 立即主动刷新，不等 2 秒轮询）。
     * - 失败：toast 按分类、对话框 3 保持变「重试」、保持勾选与目标；仅 SourceDeleteFailed
     *   例外同时刷新（源可能已部分移除，即刻如实反映，§6.6），其余失败态源未动不刷新。
     */
    fun executeMove() {
        val target = moveTarget ?: return
        val keys = selectedKeys
        if (keys.isEmpty()) {
            // 防御：确认停留期间选中集被清空（轮询剔除/SAF 失效退出）——无条目可移，关流程保多选语义
            closeMoveFlow()
            return
        }
        val source = getCurrentMarkdownFile()
        val targetMd = FileBasedMarkdownFile(context, target.file)
        moveExecuting = true
        coroutineScope.launch {
            var cancelledReminders = 0
            var firstMoveGuide = false
            val result = withContext(Dispatchers.IO) {
                val r = TopicMoveTransaction.move(source, targetMd, keys)
                if (r is MoveResult.Success) {
                    // §8.2（①仅 Success 分支内；②以执行时点 sidecar 为准；③源重读解析）
                    val store = ReminderStore.get(context)
                    val unfiredTs = store.allKeys().filterTo(mutableSetOf()) { store.hasUnfired(it) }
                    val remainingTs = runCatching {
                        EntryParser.parse(source.readAll()).mapTo(mutableSetOf()) { it.rawTimestamp }
                    }.getOrNull() // 源读失败 → null → 保守不取消（§8.2 ③，孤儿对账兜底）
                    val toCancel = remindersToCancel(
                        selectedTs = keys.mapTo(mutableSetOf()) { it.timestamp },
                        unfiredReminderTs = unfiredTs,
                        remainingSourceTs = remainingTs
                    )
                    for (ts in toCancel) {
                        // ④ 与删除条目联动同款（成对调用，对齐删除路径先例）
                        AlarmScheduler.cancel(context, ts)
                        store.remove(ts)
                    }
                    cancelledReminders = toCancel.size
                    // 首次指路（§15.4 U1）：仅 moved>0 的 Success 消耗旗标（N=0 不消耗不展示）
                    if (r.moved > 0) {
                        firstMoveGuide = topicRepository.consumeFirstMoveGuide()
                    }
                }
                r
            }
            moveExecuting = false
            when (result) {
                is MoveResult.Success -> {
                    closeMoveFlow()
                    exitSelectionMode()
                    // toast 三态（§9.1；N＝moved 实写数）＋⑤ K>0 追加附注（K＝实际取消数）
                    // ＋首次指路后缀（§15.4 U1：拼接序末位，单 toast 原则；moved=0 不展示不消耗）
                    val base = when {
                        result.moved == 0 -> Microcopy.toastMovedAllExisting(result.skippedExisting)
                        result.skippedExisting > 0 ->
                            Microcopy.toastMovedCountSkipped(result.moved, result.skippedExisting)
                        else -> Microcopy.toastMovedCount(result.moved)
                    }
                    var msg =
                        if (cancelledReminders > 0) base + Microcopy.removedRemindersSuffix(cancelledReminders)
                        else base
                    if (firstMoveGuide) msg += Microcopy.firstMoveGuideSuffix
                    showToast(context, msg)
                    // §6.6：立即主动刷新（mtime 双保险，不等 2 秒轮询）
                    fileRepository.setFileLastModified(System.currentTimeMillis())
                    refreshEntryCount()
                }
                is MoveResult.SourceDeleteFailed -> {
                    moveFailed = true // 对话框 3 保持变「重试」、保持勾选与目标（84(b)）
                    showToast(context, Microcopy.toastMoveFailedSourceDelete())
                    // §6.6 例外：源可能已部分移除，即刻如实反映；其余失败态不刷新（见下）
                    fileRepository.setFileLastModified(System.currentTimeMillis())
                    refreshEntryCount()
                }
                is MoveResult.SourceReadFailed -> {
                    moveFailed = true
                    showToast(
                        context,
                        // SAF 失效场景叠加 TD-021 句式（§9.1）；默认模式源读失败用统一兜底句式
                        if (useSAF) Microcopy.TOAST_MOVE_FAILED_SAF_REVOKED
                        else Microcopy.TOAST_MOVE_FAILED
                    )
                }
                is MoveResult.TargetReadFailed, is MoveResult.TargetWriteFailed -> {
                    moveFailed = true
                    showToast(context, Microcopy.toastMoveFailedTargetWrite())
                }
            }
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
        coroutineScope.launch {
            // 归档段（异常 → Failed，E-1）
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    archiveFile(context, mdFile, fileRepository.getArchiveFile())
                }.getOrDefault(ArchiveOutcome.Failed)
            }
            val archiveName = (outcome as? ArchiveOutcome.Archived)?.file?.name ?: ""
            val action = archiveActionFor(outcome, path, archiveName)

            if (outcome is ArchiveOutcome.Archived && action.shouldClear) {
                // 清空段（异常/返回 false → 清空失败文案，归档已保存；E-1）
                val cleared = withContext(Dispatchers.IO) {
                    runCatching { mdFile.clear() }.getOrDefault(false)
                }
                if (!cleared) {
                    showToast(context, clearFailedAction().toast!!)
                    resyncArchiveState()
                    return@launch
                }
                // 取消全部提醒（需求 63 联动保持，与旧清空路径同款）
                val store = ReminderStore.get(context)
                store.allKeys().forEach { AlarmScheduler.cancel(context, it) }
                store.removeAll()
            }

            action.toast?.let { showToast(context, it) }

            // onComplete 等价动作（A-2）：两路径共用重同步三状态；归档并清空另加
            // clearFileLastModified（替换口径：原 setFileLastModified 改清键——同驱动轮询刷新，
            // 清空后文件已空、清键语义更准）＋refreshEntryCount（承接原计数清零）；
            // 只归档丢弃 clearFileLastModified（主文件未动，remove key 为无害 no-op，A-2 附由）。
            resyncArchiveState()
            if (action.shouldClear) {
                fileRepository.clearFileLastModified()
                refreshEntryCount()
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

        // 应用启动即重排提醒（TD-014：覆盖安装/force-stop 会丢闹钟，不能只等开机广播）
        coroutineScope.launch(Dispatchers.IO) { rescheduleAll(context) }

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
                        containerColor = MaterialTheme.colorScheme.surface
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
                                text = { Text("提醒自检") },
                                onClick = {
                                    showMenu = false
                                    showSelfCheck = true
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
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
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
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 归档清空二选一对话框（修订二 93 方案 X：替换旧清空确认框；旧框标题/正文/直写族随之退役）
    if (showArchiveDialog) {
        val archiveAndClearFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { archiveAndClearFocus.requestFocus() } // 93：默认焦点落「归档并清空」
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = {
                // 路径选择非破坏（§15.3 C6）：标题默认色；破坏性警示下沉到「归档并清空」按钮
                Text(Microcopy.DLG_ARCHIVE_TITLE, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(Microcopy.dlgArchiveBody(entryCount))
                    Spacer(modifier = Modifier.height(16.dp))
                    // 路径一（78(a)：清空属破坏类＝error 红填充；＝原「清空」语义——需求 13 先备份后清空）
                    Button(
                        onClick = {
                            showArchiveDialog = false
                            runArchiveAction(ArchivePath.ARCHIVE_AND_CLEAR)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(archiveAndClearFocus),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(Microcopy.BTN_ARCHIVE_AND_CLEAR)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // 路径二（78(b)：备份非破坏＝primary；＝原菜单「归档」语义——需求 12 快照不清空）
                    OutlinedButton(
                        onClick = {
                            showArchiveDialog = false
                            runArchiveAction(ArchivePath.ARCHIVE_ONLY)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(Microcopy.BTN_ARCHIVE_ONLY)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
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
                        placeholder = { Text(Microcopy.PLACEHOLDER_EDIT) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // C 设提醒（主操作）。需求 75"次级动作先落盘"：空校验 → 有修改先保存
                    //（失败 return，框保持）→ pendingReminderTs 赋值（关框前！选择器/替换确认只读它，
                    // 漏写会因守卫静默不弹）→ 关框+收键盘 → 再弹选择器/替换确认（替换确认在保存之后）
                    OutlinedButton(
                        onClick = {
                            if (editContent.isBlank()) {
                                showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                                return@OutlinedButton
                            }
                            val ts = selectedEntry!!.timestamp
                            if (editContent != selectedEntry!!.content) {
                                try {
                                    val mdFile = getCurrentMarkdownFile()
                                    if (mdFile.updateEntry(ts, editContent)) {
                                        fileRepository.setFileLastModified(System.currentTimeMillis())
                                        refreshEntryCount()
                                        showToast(context, Microcopy.TOAST_SAVED)
                                    } else {
                                        showToast(context, Microcopy.TOAST_SAVE_FAILED)
                                        return@OutlinedButton // 保存失败不执行后续（须在关框之前 return）
                                    }
                                } catch (e: Exception) {
                                    if (BuildConfig.DEBUG) {
                                        android.util.Log.e("MainScreen", "Failed to update entry", e)
                                    }
                                    showToast(context, Microcopy.TOAST_SAVE_FAILED)
                                    return@OutlinedButton
                                }
                            }
                            pendingReminderTs = ts
                            showDetailDialog = false
                            selectedEntry = null
                            editContent = ""
                            keyboardController?.hide()
                            if (ReminderStore.get(context).hasUnfired(ts)) {
                                showOverwriteReminder = true
                            } else {
                                showReminderPicker = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("⏰ 设提醒", fontWeight = FontWeight.Medium)
                    }
                    // A 添加到日历（次要，缩小）。同"次级动作先落盘"（需求 75）：保存成功后关框再跳日历；
                    // 日历失败独立反馈、不回滚已保存内容
                    TextButton(
                        onClick = {
                            if (editContent.isBlank()) {
                                showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                                return@TextButton
                            }
                            if (editContent != selectedEntry!!.content) {
                                try {
                                    val mdFile = getCurrentMarkdownFile()
                                    if (mdFile.updateEntry(selectedEntry!!.timestamp, editContent)) {
                                        fileRepository.setFileLastModified(System.currentTimeMillis())
                                        refreshEntryCount()
                                        showToast(context, Microcopy.TOAST_SAVED)
                                    } else {
                                        showToast(context, Microcopy.TOAST_SAVE_FAILED)
                                        return@TextButton // 保存失败不执行后续（须在关框之前 return）
                                    }
                                } catch (e: Exception) {
                                    if (BuildConfig.DEBUG) {
                                        android.util.Log.e("MainScreen", "Failed to update entry", e)
                                    }
                                    showToast(context, Microcopy.TOAST_SAVE_FAILED)
                                    return@TextButton
                                }
                            }
                            val entry = CalendarEntryMapper.map(editContent)
                            showDetailDialog = false
                            selectedEntry = null
                            editContent = ""
                            keyboardController?.hide()
                            if (!CalendarLauncher.launch(context, entry)) {
                                showToast(context, Microcopy.TOAST_NO_CALENDAR)
                            }
                        }
                    ) {
                        Text("添加到日历", style = MaterialTheme.typography.labelMedium)
                    }
                    if (ReminderStore.get(context).hasUnfired(selectedEntry!!.timestamp)) {
                        TextButton(
                            onClick = {
                                AlarmScheduler.cancel(context, selectedEntry!!.timestamp)
                                ReminderStore.get(context).remove(selectedEntry!!.timestamp)
                                showToast(context, "已取消提醒")
                            }
                        ) { Text("取消提醒", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editContent.isBlank()) {
                            showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
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
                                showToast(context, Microcopy.TOAST_SAVED)
                            } else {
                                showToast(context, Microcopy.TOAST_SAVE_FAILED)
                            }
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG) {
                                android.util.Log.e("MainScreen", "Failed to update entry", e)
                            }
                            showToast(context, Microcopy.TOAST_SAVE_FAILED)
                        }
                        showDetailDialog = false
                        selectedEntry = null
                        editContent = ""
                    }
                ) {
                    Text(Microcopy.BTN_SAVE, color = MaterialTheme.colorScheme.primary)
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

    // 提醒时间选择
    if (showReminderPicker) {
        val ts = pendingReminderTs
        if (ts != null) {
            ReminderTimePickerDialog(
                onPick = { triggerAt, recurrence ->
                    showReminderPicker = false
                    if (triggerAt <= System.currentTimeMillis()) {
                        showToast(context, "请选择未来时间")
                    } else {
                        startSetReminder(ts, triggerAt, recurrence)
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
            title = {
                Text(
                    "替换已有提醒？",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = { Text("该记录已设提醒，确定替换为新时间？") },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteReminder = false
                    showReminderPicker = true
                }) { Text("替换", color = MaterialTheme.colorScheme.error) }
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
                Column {
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
                    Spacer(modifier = Modifier.height(12.dp))
                    // 并列次要入口（需求 75"次级动作先落盘"）：先追加落盘（toast"已追加"，不带重复旁注）
                    // → 清空/关框/收键盘 → 再开提醒选择器。新条目时间戳经 appendReturningTimestamp 在
                    // 同一持锁事务内取得（消除"追加后重读末条"的并发插入竞态）；返回 null ⟺ 追加未发生
                    OutlinedButton(
                        onClick = {
                            if (inputContent.isBlank()) {
                                showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                                return@OutlinedButton
                            }
                            try {
                                val mdFile = getCurrentMarkdownFile()
                                val newTs = mdFile.appendReturningTimestamp(inputContent)
                                if (newTs != null) {
                                    fileRepository.setFileLastModified(System.currentTimeMillis())
                                    refreshEntryCount()
                                    showToast(context, Microcopy.TOAST_APPENDED)
                                    inputContent = ""
                                    showInputDialog = false
                                    keyboardController?.hide()
                                    pendingReminderTs = newTs
                                    showReminderPicker = true
                                } else {
                                    showToast(context, Microcopy.TOAST_APPEND_FAILED)
                                }
                            } catch (_: Exception) {
                                showToast(context, Microcopy.TOAST_APPEND_FAILED)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("⏰ 设提醒", fontWeight = FontWeight.Medium)
                    }
                    TextButton(
                        onClick = {
                            if (inputContent.isBlank()) {
                                showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                                return@TextButton
                            }
                            try {
                                val mdFile = getCurrentMarkdownFile()
                                if (mdFile.appendReturningTimestamp(inputContent) != null) {
                                    fileRepository.setFileLastModified(System.currentTimeMillis())
                                    refreshEntryCount()
                                    val entry = CalendarEntryMapper.map(inputContent)
                                    showToast(context, Microcopy.TOAST_APPENDED)
                                    inputContent = ""
                                    showInputDialog = false
                                    keyboardController?.hide()
                                    if (!CalendarLauncher.launch(context, entry)) {
                                        showToast(context, Microcopy.TOAST_NO_CALENDAR)
                                    }
                                } else {
                                    showToast(context, Microcopy.TOAST_APPEND_FAILED)
                                }
                            } catch (_: Exception) {
                                showToast(context, Microcopy.TOAST_APPEND_FAILED)
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
                        // 空白校验（需求 76a）：toast 且不关框、不清输入；纯空白字符视同空（isBlank）
                        if (inputContent.isBlank()) {
                            showToast(context, Microcopy.TOAST_CONTENT_EMPTY)
                            return@TextButton
                        }
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
                                    Microcopy.toastAppendedWithDuplicates(existingSameCount)
                                } else {
                                    Microcopy.TOAST_APPENDED
                                }
                                showToast(context, msg)
                            } else {
                                showToast(context, Microcopy.TOAST_APPEND_FAILED)
                            }
                        } catch (_: Exception) {
                            showToast(context, Microcopy.TOAST_APPEND_FAILED)
                        }
                        inputContent = ""
                        showInputDialog = false
                        keyboardController?.hide()
                    }
                ) {
                    Text(Microcopy.BTN_SAVE, color = MaterialTheme.colorScheme.primary)
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

    // —— 「移入主题」流程对话框栈（T-06，§8.1；对话框本体在 TopicMoveDialogs.kt）——
    when (moveFlowStep) {
        MoveFlowStep.SELECT_TARGET -> TopicSelectDialog(
            topics = moveTopics, // createdAt 倒序（TopicRepository.listTopics 序）
            onSelect = { topic ->
                // 点选已有主题 → 对话框 3（§8.1）
                moveTarget = topic
                moveFailed = false
                moveFlowStep = MoveFlowStep.CONFIRM
            },
            onNewTopic = { moveFlowStep = MoveFlowStep.NEW_TOPIC },
            // 点外/返回关全部——保持多选态与选中集（与对话框 3「取消」同处置）
            onDismiss = ::closeMoveFlow
        )
        MoveFlowStep.NEW_TOPIC -> NewTopicNameDialog(
            name = newTopicInput,
            onNameChange = { newTopicInput = it },
            onCreate = {
                // 校验链＋创建一体经 createEmptyTopic（原子，无「先校验后创建」间隙）；
                // 校验各失败分支 → 对应 toast 且对话框保持（§8.1）；取消留空文档不回收（§6.5）
                coroutineScope.launch {
                    val created = withContext(Dispatchers.IO) {
                        topicRepository.createEmptyTopic(newTopicInput)
                    }
                    when (created) {
                        is TopicCreateResult.Created -> {
                            // 进确认框前已建空文档（§6.5 创建时机取舍）：确认框取消留空文档，可删可复用
                            moveTarget = TopicDocument(
                                file = created.file,
                                displayName = newTopicInput.trim(),
                                createdAt = System.currentTimeMillis(),
                                entryCount = 0
                            )
                            moveFailed = false
                            moveFlowStep = MoveFlowStep.CONFIRM
                        }
                        is TopicCreateResult.NameExists -> {
                            // 同名 → 对话框 2b 指路（83 修订 c）
                            sameNameExisting = created.existing
                            moveFlowStep = MoveFlowStep.SAME_NAME
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
            onDismiss = { moveFlowStep = MoveFlowStep.SELECT_TARGET } // 「取消」→ 回对话框 1（§8.1）
        )
        MoveFlowStep.SAME_NAME -> SameNameDialog(
            existingName = sameNameExisting?.displayName ?: "",
            onMoveToExisting = {
                // 「移入该主题」→ 目标切该已有主题，直达对话框 3（§8.1）
                moveTarget = sameNameExisting
                sameNameExisting = null
                moveFailed = false
                moveFlowStep = MoveFlowStep.CONFIRM
            },
            onDismiss = { moveFlowStep = MoveFlowStep.NEW_TOPIC } // 「取消」→ 回对话框 2，输入保留（§8.1）
        )
        MoveFlowStep.CONFIRM -> MoveConfirmDialog(
            count = selectedKeys.size, // N＝所选条数（84(d)/88 口径）
            topicName = moveTarget?.displayName ?: "",
            // K 预览（§8.2 ②：执行取消在成功回调内重算，此处仅确认框前置告知）
            reminderCount = selectedKeys.count { ReminderStore.get(context).hasUnfired(it.timestamp) },
            executing = moveExecuting,
            failed = moveFailed,
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

private fun scheduleReminder(context: android.content.Context, ts: String, triggerAt: Long, recurrence: Recurrence = Recurrence.NONE) {
    AlarmScheduler.schedule(context, ts, triggerAt)
    ReminderStore.get(context).set(ts, ReminderMeta(triggerAt = triggerAt, fired = false, snoozedUntil = 0, recurrence = recurrence))
    showToast(context, "已设置提醒")
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

private fun copyContent(context: android.content.Context, mdFile: MarkdownFileOperations) {
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

private fun shareContent(context: android.content.Context, mdFile: MarkdownFileOperations) {
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

/** 归档执行结果（修订二 93 ④：archiveFile 重构返回通道——写盘段逐字节等价，仅返回值与 toast 通道变更）。 */
sealed interface ArchiveOutcome {
    /** 归档副本已生成。 */
    data class Archived(val file: File) : ArchiveOutcome

    /**
     * 空内容（仅文件头）。**行为变更（A-3/T-3）**：现状（重构前）返 true 且调用方继续清空；
     * 重构后语义＝不清空，由调用方按决策映射提示（入口守卫配套防呆，R15 陈旧窗口下可达）。
     */
    object NothingToArchive : ArchiveOutcome

    /** 归档失败（含异常；TD-017：调用方不得清空）。 */
    object Failed : ArchiveOutcome
}

/** 归档清空二选一路径（修订二 93 方案 X）。 */
enum class ArchivePath { ARCHIVE_AND_CLEAR, ARCHIVE_ONLY }

/** 二选一决策映射产物（§17.1 M1：编排层只执行决定，不自行判断）。 */
data class ArchiveAction(
    val toast: String?,
    val shouldClear: Boolean,
    val shouldCancelAllReminders: Boolean
)

/**
 * 归档二选一决策映射（§17.1 M1 ①~④，纯函数 JVM 可测）：
 * Archived×归档并清空→归并 toast＋清空＋取消全部提醒；Archived×只归档→只报备不清空不动提醒；
 * Failed×两路径→两失败字面且均不清空（TD-017）；NothingToArchive×两路径→守卫字面无动作。
 */
internal fun archiveActionFor(
    outcome: ArchiveOutcome,
    path: ArchivePath,
    archiveName: String
): ArchiveAction = when (outcome) {
    is ArchiveOutcome.Archived -> when (path) {
        ArchivePath.ARCHIVE_AND_CLEAR -> ArchiveAction(
            toast = Microcopy.toastArchivedAndCleared(archiveName),
            shouldClear = true,
            shouldCancelAllReminders = true
        )
        ArchivePath.ARCHIVE_ONLY -> ArchiveAction(
            toast = Microcopy.toastArchivedTo(archiveName),
            shouldClear = false,
            shouldCancelAllReminders = false
        )
    }
    ArchiveOutcome.Failed -> when (path) {
        // TD-017：归档失败不得清空——归档并清空路径明示「未清空」
        ArchivePath.ARCHIVE_AND_CLEAR -> ArchiveAction(Microcopy.TOAST_ARCHIVE_FAILED_CLEAR_KEPT, false, false)
        ArchivePath.ARCHIVE_ONLY -> ArchiveAction(Microcopy.TOAST_ARCHIVE_FAILED, false, false)
    }
    ArchiveOutcome.NothingToArchive -> ArchiveAction(Microcopy.TOAST_NOTHING_TO_ARCHIVE, false, false)
}

/** 清空段失败映射（§17.1 M1 ⑤：两路径同字面——归档已保存，仅清空未完成）。 */
internal fun clearFailedAction(): ArchiveAction =
    ArchiveAction(Microcopy.TOAST_CLEAR_FAILED_ARCHIVED, shouldClear = false, shouldCancelAllReminders = false)

/**
 * Archive current content to a new file（修订二 93 ④ 重构）。
 * **writeAll 写盘段与重构前逐字节等价**（等价宣称限于写盘段，A-3/T-3）；
 * toast 全部上移调用方（经 [archiveActionFor] 决策映射）；onComplete 通道改为调用方
 * 编排（runArchiveAction 的 resyncArchiveState 等价动作）；
 * 未使用的 fileRepository 参数已删除（E-1）；调用方负责 IO 线程（A-9）。
 */
private fun archiveFile(
    context: android.content.Context,
    currentMdFile: MarkdownFileOperations,
    archiveFile: File
): ArchiveOutcome {
    return try {
        // Read current content BEFORE creating new archive file
        val currentContent = currentMdFile.readAll()

        // Check if content is empty (only has header, no actual entries)
        if (currentContent.isBlank() || !currentContent.contains("## ")) {
            return ArchiveOutcome.NothingToArchive
        }

        // 通过 MarkdownFileOperations.writeAll 写归档（v1.1 CB6：享 atomicWrite + 锁 + 同源同格式 ZWSP）
        val contentWithoutHeader = if (currentContent.startsWith(MarkdownFormatter.FILE_HEADER)) {
            currentContent.substring(MarkdownFormatter.FILE_HEADER.length)
        } else {
            currentContent
        }
        val archiveMdFile = FileBasedMarkdownFile(context, archiveFile)
        if (!archiveMdFile.writeAll(MarkdownFormatter.FILE_HEADER + contentWithoutHeader)) {
            return ArchiveOutcome.Failed // TD-017：失败上抛，调用方不得继续清空
        }

        ArchiveOutcome.Archived(archiveFile)
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("MainScreen", "Archive failed", e)
        }
        ArchiveOutcome.Failed
    }
}
