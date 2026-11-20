package com.yiyue31.android.appendo.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.TopicCreateResult
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.util.EntryKey
import com.yiyue31.android.appendo.util.CalendarEntryMapper
import com.yiyue31.android.appendo.util.CalendarLauncher
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.util.FileBasedMarkdownFile
import com.yiyue31.android.appendo.util.MarkdownFileFactory
import com.yiyue31.android.appendo.util.MarkdownFileOperations
import com.yiyue31.android.appendo.util.MoveResult
import com.yiyue31.android.appendo.util.TopicMoveTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 主题工作区屏（戊批次 T-25 骨架＋T-26 多选/移出/转移，需求 96/97，设计 §21.2/§22.1）：
 * 顶栏（返回＋主题名 primary SemiBold＋「N 条」小字）＋条目列表＋多选 contextual bar
 * （「移出到主列表」「移入其他主题」两钮——action 槽注入）＋移出/转移流程对话框栈
 * （[MoveFlowHolder] 状态＋[MoveOutcomeMapping] 产出映射）。
 *
 * - L1 期浏览态动作面＝零（编辑/删除/新增/出口＝L2/L3，T-29/T-31）；多选态长按＝预览（§22.4）；
 * - 转移目标选择排除当前主题（SameDocument 第一道防线；事务层护栏兜底）＋D2 新建主题子流程
 *   （复用移入对话框 2/2b：NewTopicNameDialog＋SameNameDialog，创建成功即为目标）；
 * - 执行：Dispatchers.IO 内 move（沿 executeMove 先例）；成功→invalidate(源＋目标)（§20.2
 *   统一规则）＋移出方向 setFileLastModified（评审 A12：工作区在栈顶，主列表经返回时轮询/
 *   初始加载自刷）＋本页重载＋选中集 retainedSelection 保留；失败→对话框保持变「重试」；
 * - 读失败如实呈现／L1 中性空态（T-25 语义不变；深链定位随自建提醒退役删除——R 批次，specs 16.C）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicWorkspaceScreen(
    filePath: String,
    fileRepository: FileRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val topicFile = remember(filePath) { File(filePath) }
    val topicName = remember(filePath) { topicFile.name.removeSuffix(".md") }
    val topicRepository = remember { TopicRepository(context) }
    // null＝加载中；空表＝空态（0 条合法）
    var entries by remember(filePath) { mutableStateOf<List<LinkEntry>?>(null) }
    var loadFailed by remember(filePath) { mutableStateOf(false) }
    var reloadKey by remember(filePath) { mutableIntStateOf(0) }
    // F1 标题行计数（16.D 修订注一 C1）：仅成功分支赋值——重载不闪 0、读失败保留旧值；
    // filePath 键控＝切主题重置（聚焦工程评审 #3：不依赖「加载中不渲染标题行」隐式前提）
    var titleCount by remember(filePath) { mutableIntStateOf(0) }
    var hasLoaded by remember(filePath) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // —— 多选态（§7.2/§22.4）＋N-4 saveable（过重建）——
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(stateSaver = EntryKeySetSaver) { mutableStateOf<Set<EntryKey>>(emptySet()) }
    var previewEntry by rememberSaveable(stateSaver = LinkEntrySaver) { mutableStateOf<LinkEntry?>(null) }

    // —— 编辑/删除（WB-2，102(a)(b)(c)；草稿三件套宿主镜像 §2.2）＋N-4 saveable——
    var editEntry by rememberSaveable(stateSaver = LinkEntrySaver) { mutableStateOf<LinkEntry?>(null) }
    var editDraft by rememberSaveable { mutableStateOf("") }
    var lastEditedTs by rememberSaveable(stateSaver = NullableStringSaver) { mutableStateOf<String?>(null) }
    var deleteTargetTs by rememberSaveable(stateSaver = NullableStringSaver) { mutableStateOf<String?>(null) }
    var showBatchDelete by rememberSaveable { mutableStateOf(false) }

    // —— 新增/归档/溢出菜单（WB-3，102(d)(e)(g)）；瞬态 showOverflow 不保全（N-4 分类）——
    var showInputDialog by rememberSaveable { mutableStateOf(false) }
    var inputDraft by rememberSaveable { mutableStateOf("") }
    var showArchiveDialog by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    // code-review 修：仅新增后定位顶部（编辑/删除重载不跳顶，保留滚动位置）
    var scrollAfterAdd by remember { mutableStateOf(false) }

    // —— 移出/转移流程（MoveFlowHolder，T-26）＋N-4 saveable（executing 暂记 failed＋重推导标记）——
    val moveFlow = rememberSaveable(saver = MoveFlowHolderSaver) { MoveFlowHolder() }
    var topics by remember { mutableStateOf<List<TopicDocument>>(emptyList()) }
    var sameNameExisting by remember { mutableStateOf<TopicDocument?>(null) }

    // 主文件三态快照（移出目标构造；读一次即快照——切换存储为低频路径，流程内一致性优先）
    val useSAF = remember { fileRepository.isUsingSAF() }
    val fileUri = remember { fileRepository.getFileUri() }
    val defaultFile = remember { fileRepository.getDefaultFile() }

    LaunchedEffect(filePath, reloadKey) {
        entries = null
        loadFailed = false
        val result = withContext(Dispatchers.IO) {
            runCatching { FileBasedMarkdownFile(context, topicFile).readAllWithStatus() }.getOrNull()
        }
        if (result == null || result.failed) {
            loadFailed = true
            scrollAfterAdd = false // 聚焦评审 #6：读失败也重置——防重试成功后意外跳顶
        } else {
            val loaded = EntryParser.parse(result.content).map { LinkEntry.from(it) }
            entries = loaded
            // F1（修订注一 C1）：计数仅成功分支赋值——重载期保留旧值无 N→0→N 闪烁
            titleCount = loaded.size
            hasLoaded = true
            // code-review 修：仅新增后定位顶部（倒序新条恒首条目——F1 headerSlot 后＝index 1）；编辑/删除保留滚动位置
            if (scrollAfterAdd) {
                scrollAfterAdd = false
                if (loaded.isNotEmpty()) listState.scrollToItem(1)
            }
            // 重载后保留仍存在的选中项（§22.4：retainedSelection 复用——被删剔除、新插入未选）
            selectedKeys = retainedSelection(selectedKeys, loaded)
            // N-4 陈旧校验（TD-051 §3.4）：恢复目标不存在→关框留草稿（editDraft 归属 lastEditedTs 保留）
            if (editEntry != null && loaded.none { it.timestamp == editEntry?.timestamp }) {
                editEntry = null
            }
            if (deleteTargetTs != null && loaded.none { it.timestamp == deleteTargetTs }) {
                deleteTargetTs = null
            }
            // N-4 executing 重推导（TD-051 §3.3）：选中集已过滤后判定真实结局
            if (moveFlow.restoredFromExecuting) {
                moveFlow.consumeRestoredFromExecuting()
                when (MoveRestoreDecision.decide(true, selectedKeys.isEmpty())) {
                    MoveRestoreDecision.Action.CLOSE_FLOW -> {
                        moveFlow.close() // 事务实际已完成——静默收尾
                        selectionMode = false // ＝exitSelectionMode()（局部函数后置声明，此处内联）
                        selectedKeys = emptySet()
                    }
                    else -> Unit // KEEP_RETRY＝暂记 failed 重试面貌；NONE 不可达（标记已守卫）
                }
            }
        }
    }

    /** 编辑保存核心（/simplify 收敛共享 tryEditSave；不清对话框状态，调用方成功后清）。 */
    fun performEditSave(): Boolean {
        val entry = editEntry ?: return false
        return tryEditSave(context, FileBasedMarkdownFile(context, topicFile), entry.timestamp, entry.content, editDraft) {
            reloadKey++
        }
    }

    /** 追加保存核心（共享 tryAppend；查重旁注经主题基句）。 */
    fun performAppendSave(): Boolean =
        tryAppend(context, FileBasedMarkdownFile(context, topicFile), inputDraft, Microcopy.toastAppendedToTopic(topicName), true) {
            scrollAfterAdd = true
            reloadKey++
        } != null

    /** 追加供日历核心（75 先落盘；不带查重旁注）。返回落盘内容供预填，失败 null。 */
    fun performAppendForCalendar(): String? =
        tryAppend(context, FileBasedMarkdownFile(context, topicFile), inputDraft, Microcopy.toastAppendedToTopic(topicName), false) {
            scrollAfterAdd = true
            reloadKey++
        }

    /** 归档二选一共享编排（/simplify 收敛：两回调仅 path 不同）。 */
    fun archiveWith(path: ArchivePath) {
        showArchiveDialog = false
        runArchive(
            context = context, scope = coroutineScope,
            sourceMd = FileBasedMarkdownFile(context, topicFile),
            getTarget = { fileRepository.getArchiveFile(topicName) },
            wordbook = topicArchiveWordbook(topicName),
            path = path,
            onCompleted = { _, _ -> reloadKey++ }
        )
    }

    /** 同 key 全删（102(b)；deleteEntries 按 EntryKey 寻址）。 */
    fun deleteKeys(keys: Set<EntryKey>) {
        try {
            val md = FileBasedMarkdownFile(context, topicFile)
            if (md.deleteEntries(keys) != null) {
                showToast(context, Microcopy.TOAST_ENTRY_DELETED)
                reloadKey++
            } else {
                showToast(context, Microcopy.TOAST_ENTRY_DELETE_FAILED)
            }
        } catch (e: Exception) {
            showToast(context, Microcopy.TOAST_ENTRY_DELETE_FAILED)
        }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    // §7.2：系统返回手势退出多选并清空选中集（仅多选态拦截；沿 MainScreen 先例）
    BackHandler(enabled = selectionMode) { exitSelectionMode() }

    fun startMoveOut() {
        moveFlow.start(MoveVerb.MOVE_OUT, MoveFlowStep.CONFIRM)
    }

    fun startTransfer() {
        coroutineScope.launch {
            // 目标列表锁外取（listTopics 持锁；打开对话框前一次快照）
            topics = withContext(Dispatchers.IO) { topicRepository.listTopics() }
            moveFlow.start(MoveVerb.TRANSFER, MoveFlowStep.SELECT_TARGET)
        }
    }

    fun executeMove() {
        val keys = selectedKeys
        if (keys.isEmpty()) {
            moveFlow.close()
            return
        }
        val verb = moveFlow.verb ?: return
        val source: MarkdownFileOperations = FileBasedMarkdownFile(context, topicFile)
        val target: MarkdownFileOperations = if (verb == MoveVerb.MOVE_OUT) {
            MarkdownFileFactory.create(context, useSAF, fileUri, defaultFile)
        } else {
            val targetTopic = moveFlow.transferTarget ?: return
            FileBasedMarkdownFile(context, targetTopic.file)
        }
        moveFlow.markExecuting(true)
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { TopicMoveTransaction.move(source, target, keys) }
            moveFlow.markExecuting(false)
            val targetName =
                if (verb == MoveVerb.MOVE_OUT) "主列表" else moveFlow.transferTarget?.displayName.orEmpty()
            val outcome = MoveOutcomeMapping.map(result, verb, useSAF, topicName, targetName)
            when (result) {
                is MoveResult.Success -> {
                    // 移出方向：主列表刷新信号（A12——不直接调用 MainScreen 方法）；
                    // 缓存失效随自建提醒退役删除（R 批次，specs 16.C）
                    if (outcome.refreshMain) {
                        fileRepository.setFileLastModified(System.currentTimeMillis())
                    }
                    moveFlow.close()
                    exitSelectionMode()
                    reloadKey++
                }
                is MoveResult.SameDocument -> Unit // 防御路径（96）：只提示，不动选择上下文
                else -> moveFlow.markFailed() // 对话框保持变「重试」（84(b) 幂等收敛）
            }
            outcome.toast?.let { showToast(context, it) }
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                MultiSelectTopBar(
                    selectedCount = selectedKeys.size,
                    // F1 约束③：多选长选滚动场景主题上下文（区分度真机后重评——聚焦评审 #5）
                    contextName = topicName,
                    onExit = { exitSelectionMode() },
                    onMoveIn = {}, // 缺省单钮不被消费——action 槽两钮注入（§21.2）
                    action = {
                        TextButton(
                            onClick = { startMoveOut() },
                            enabled = selectedKeys.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(Microcopy.BTN_MOVE_OUT)
                        }
                        TextButton(
                            onClick = { startTransfer() },
                            enabled = selectedKeys.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(Microcopy.BTN_MOVE_TO_OTHER)
                        }
                        // WB-2 第三钮「删除」（102(c)；确认框明示 N）
                        TextButton(
                            onClick = { showBatchDelete = true },
                            enabled = selectedKeys.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                    }
                )
            } else {
                TopAppBar(
                    // F1（16.D 修订注一）：主题名＋计数移顶栏下方标题行（随列表滚动）；title 槽清空——
                    // 空槽后 actions 水平左移＝预期（动作集不变；聚焦工程评审 #2 注明接受）
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        // WB-3 顶栏三动作「＋/复制全部/分享」（102(e)；归档收溢出菜单）
                        IconButton(onClick = { showInputDialog = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "新增")
                        }
                        TextButton(onClick = {
                            copyContent(context, FileBasedMarkdownFile(context, topicFile))
                        }) { Text("复制全部") }
                        IconButton(onClick = {
                            shareContent(context, FileBasedMarkdownFile(context, topicFile))
                        }) { Icon(Icons.Default.Share, contentDescription = "分享全部") }
                        // 溢出菜单：归档二选一入口（102(g)）
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("归档…") },
                                onClick = { showOverflow = false; showArchiveDialog = true }
                            )
                        }
                        // 多选入口（§7.1 方案 A）：条目数为 0 时隐藏
                        if (!entries.isNullOrEmpty()) {
                            IconButton(onClick = { selectionMode = true }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "多选")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        // v1.4 视觉裁定：顶栏随页面底色，与内容区无缝
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
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
                    // 读失败如实呈现（评审 C3）＋重试（readAllWithStatus 重跑）；
                    // F1：标题行常驻分支顶部，计数保留上次成功值（不得呈错 0——修订注一 C1）
                    Column {
                        TopicTitleRow(topicName, titleCount, hasLoaded)
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
                }
                entries == null -> {
                    // 加载中（沿 ArchiveDetailScreen「加载中...」先例）；
                    // F1：标题行常驻，首载计数隐藏（hasLoaded=false——不呈 0），重载期保留旧值
                    Column {
                        TopicTitleRow(topicName, titleCount, hasLoaded)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("加载中...")
                        }
                    }
                }
                entries!!.isEmpty() -> {
                    // F1（修订注一⑥）：标题行（名＋「0 条」）＋仅引导句——「暂无条目」行退役
                    //（0 条已由标题行计数表达，去重）；EMPTY_BODY 引导句保留（TC-042 判据不动）
                    Column {
                        TopicTitleRow(topicName, titleCount, true)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
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
                        readOnly = false, // 徽标正向启用（K13 反向）
                        headerText = "", // F1：固定 header 退役——标题行经 headerSlot 承载
                        headerSlot = { TopicTitleRow(topicName, titleCount, true) }, // 随列表滚动（修订注一①）
                        selectionMode = selectionMode,
                        selectedKeys = selectedKeys,
                        onToggleSelection = { entry ->
                            // 与 MainScreen 同款勾选切换（§7.3：按 key 查 Set）
                            val key = entry.entryKey
                            selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                        },
                        onPreview = { previewEntry = it },
                        // WB-2：编辑入口（102(a)；草稿同条目保留/换条目重置 §2.2）
                        onEntryClick = { entry ->
                            if (lastEditedTs != entry.timestamp) editDraft = entry.content
                            lastEditedTs = entry.timestamp
                            editEntry = entry
                        },
                        // WB-2：右滑删除（102(b)/105——指示与动作一致）
                        onEntrySwipeToDelete = { ts -> deleteTargetTs = ts },
                        // WB-3：长按单条复制（102(e)；出口干净——parse 产物无 ZWSP）
                        onEntryLongClick = { content ->
                            performVibration(context, VIBRATION_DURATION_SHORT_MS)
                            copyToClipboard(context, content)
                        }
                    )
                }
            }
        }
    }

    // —— 移出/转移对话框栈（MoveFlowHolder 驱动；复用移入对话框族，D2 新建子流程不旁路新写）——

    val currentVerb = moveFlow.verb
    when (moveFlow.step) {
        MoveFlowStep.CONFIRM -> {
            val n = selectedKeys.size
            val body = when (currentVerb) {
                MoveVerb.MOVE_OUT -> Microcopy.dlgMoveOutConfirmBody(n)
                MoveVerb.TRANSFER -> Microcopy.dlgTransferConfirmBody(
                    n, moveFlow.transferTarget?.displayName.orEmpty(), topicName
                )
                // MOVE_IN 工作区不可达（主列表专属动词）——防御兜底沿移入确认句
                MoveVerb.MOVE_IN, null -> Microcopy.dlgMoveConfirmBody(n, topicName)
            }
            MoveConfirmDialog(
                count = n,
                topicName = topicName,
                executing = moveFlow.executing,
                failed = moveFlow.failed,
                onConfirm = { executeMove() },
                onDismiss = { moveFlow.close() },
                bodyText = body,
                titleText = if (currentVerb == MoveVerb.MOVE_OUT) Microcopy.BTN_MOVE_OUT else Microcopy.DLG_MOVE_TITLE,
                confirmLabel = if (currentVerb == MoveVerb.MOVE_OUT) Microcopy.BTN_MOVE_OUT else Microcopy.BTN_MOVE_IN
            )
        }
        MoveFlowStep.SELECT_TARGET -> {
            TopicSelectDialog(
                topics = topics,
                onSelect = {
                    moveFlow.selectTarget(it)
                    moveFlow.goTo(MoveFlowStep.CONFIRM)
                },
                onNewTopic = {
                    moveFlow.goTo(MoveFlowStep.NEW_TOPIC)
                },
                onDismiss = { moveFlow.close() },
                excludeName = topicName // 排除当前主题（SameDocument 第一道防线，§21.2）
            )
        }
        MoveFlowStep.NEW_TOPIC -> {
            NewTopicNameDialog(
                name = moveFlow.newTopicInput,
                onNameChange = { moveFlow.newTopicInput = it },
                onCreate = {
                    coroutineScope.launch {
                        // 校验链＋创建一体（原子，沿 MainScreen/TopicListScreen 先例）
                        when (val r = withContext(Dispatchers.IO) {
                            topicRepository.createEmptyTopic(moveFlow.newTopicInput)
                        }) {
                            is TopicCreateResult.Created -> {
                                // D2：创建成功即为目标（刷新列表供下一轮排除/同名检测）
                                topics = withContext(Dispatchers.IO) { topicRepository.listTopics() }
                                moveFlow.selectTarget(
                                    topics.firstOrNull { it.file == r.file }
                                )
                                moveFlow.newTopicInput = ""
                                moveFlow.goTo(MoveFlowStep.CONFIRM)
                            }
                            is TopicCreateResult.NameExists -> {
                                sameNameExisting = r.existing
                                moveFlow.goTo(MoveFlowStep.SAME_NAME)
                            }
                            TopicCreateResult.NameEmpty -> showToast(context, Microcopy.TOAST_TOPIC_NAME_EMPTY)
                            TopicCreateResult.NameReserved -> showToast(context, Microcopy.TOAST_TOPIC_NAME_RESERVED)
                            TopicCreateResult.NameIllegalChars -> showToast(context, Microcopy.TOAST_TOPIC_NAME_ILLEGAL)
                            TopicCreateResult.NameTooLong -> showToast(context, Microcopy.TOAST_TOPIC_NAME_TOO_LONG)
                            TopicCreateResult.WriteFailed -> showToast(context, Microcopy.TOAST_TOPIC_CREATE_FAILED)
                        }
                    }
                },
                onDismiss = { moveFlow.goTo(MoveFlowStep.SELECT_TARGET) } // 回对话框 1（输入保留）
            )
        }
        MoveFlowStep.SAME_NAME -> {
            SameNameDialog(
                existingName = sameNameExisting?.displayName.orEmpty(),
                onMoveToExisting = {
                    moveFlow.selectTarget(sameNameExisting)
                    moveFlow.goTo(MoveFlowStep.CONFIRM)
                },
                onDismiss = { moveFlow.goTo(MoveFlowStep.NEW_TOPIC) } // 回对话框 2（输入保留）
            )
        }
        null -> Unit
    }

    // —— 新增对话框（WB-3：共享 EntryInputDialog；toast 含主题名＋定位顶部）——
    if (showInputDialog) {
        EntryInputDialog(
            draft = inputDraft,
            onDraftChange = { inputDraft = it },
            onAppend = {
                if (performAppendSave()) {
                    inputDraft = ""
                    showInputDialog = false
                } // 失败保持开框（行为修正 1）
            },
            onRequestCalendar = {
                val content = performAppendForCalendar() ?: return@EntryInputDialog
                inputDraft = ""
                showInputDialog = false
                if (!CalendarLauncher.launch(context, CalendarEntryMapper.map(content))) {
                    showToast(context, Microcopy.TOAST_NO_CALENDAR)
                }
            },
            onDismiss = { showInputDialog = false }
        )
    }

    // —— 归档二选一（WB-3：共享 ArchiveChoiceDialog；主题词族＋onCompleted 仅 reloadKey）——
    if (showArchiveDialog) {
        ArchiveChoiceDialog(
            entryCount = entries?.size ?: 0,
            topicName = topicName,
            onArchiveAndClear = { archiveWith(ArchivePath.ARCHIVE_AND_CLEAR) },
            onArchiveOnly = { archiveWith(ArchivePath.ARCHIVE_ONLY) },
            onDismiss = { showArchiveDialog = false }
        )
    }

    // —— 编辑对话框（WB-2：共享 EntryEditDialog，草稿/时序宿主）——
    editEntry?.let { entry ->
        EntryEditDialog(
            entry = entry,
            draft = editDraft,
            onDraftChange = { editDraft = it },
            onSave = {
                if (performEditSave()) {
                    editEntry = null; editDraft = ""; lastEditedTs = null
                }
            },
            onRequestCalendar = {
                val content = editDraft // 先捕获（code-review 一致性：防 core 后状态变化读空）
                if (!performEditSave()) return@EntryEditDialog
                editEntry = null; editDraft = ""; lastEditedTs = null
                if (!CalendarLauncher.launch(context, CalendarEntryMapper.map(content))) {
                    showToast(context, Microcopy.TOAST_NO_CALENDAR)
                }
            },
            onDismiss = { editEntry = null } // 仅关窗不清草稿（修订⑤）
        )
    }

    // —— 单条删除确认（WB-2：error 红＋振动；同 key 全删）——
    deleteTargetTs?.let { ts ->
        AlertDialog(
            onDismissRequest = { deleteTargetTs = null },
            title = { Text(Microcopy.DLG_ENTRY_DELETE_TITLE, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) },
            text = { Text(Microcopy.DLG_ENTRY_DELETE_BODY) },
            confirmButton = {
                TextButton(onClick = {
                    performVibration(context, VIBRATION_DURATION_SHORT_MS)
                    val target = entries?.firstOrNull { it.timestamp == ts }
                    deleteTargetTs = null
                    if (target != null) deleteKeys(setOf(target.entryKey))
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTargetTs = null }) { Text("取消") } }
        )
    }

    // —— 批量删除确认（WB-2：明示 N；删后退出多选）——
    if (showBatchDelete) {
        AlertDialog(
            onDismissRequest = { showBatchDelete = false },
            title = { Text(Microcopy.DLG_ENTRY_DELETE_TITLE, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) },
            text = { Text(Microcopy.dlgBatchDeleteBody(selectedKeys.size)) },
            confirmButton = {
                TextButton(onClick = {
                    performVibration(context, VIBRATION_DURATION_SHORT_MS)
                    val keys = selectedKeys
                    showBatchDelete = false
                    exitSelectionMode()
                    deleteKeys(keys)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBatchDelete = false }) { Text("取消") } }
        )
    }

    // 多选态长按预览（§22.4——预览含复制动作，「长按复制不被占用」）
    previewEntry?.let { entry ->
        EntryPreviewDialog(entry = entry, onDismiss = { previewEntry = null })
    }
}

/**
 * F1 标题行（16.D 修订注一，2026-11-19）：主题名＋「N 条」——顶栏下方全宽，随列表滚动（正常态＝
 * headerSlot 首项；加载/失败/空态＝分支顶部）。非交互（onSurface 而非 primary——Apple 大标题用
 * label 色，消除假 affordance；改名入口维持主题卡左滑）。容量＝2 行约 28 汉字（50 字上限名截断为
 * 已知限制）。bottom padding 4dp＋LazyColumn spacedBy(8dp)＝12dp 总间距（聚焦评审 #4 验算）。
 */
@Composable
private fun TopicTitleRow(topicName: String, titleCount: Int, countVisible: Boolean) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            topicName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (countVisible) {
            Text(
                Microcopy.topicEntryCountLine(titleCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
