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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.TopicCreateResult
import com.yiyue31.android.appendo.data.TopicDocument
import com.yiyue31.android.appendo.data.TopicRepository
import com.yiyue31.android.appendo.util.EntryKey
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
    val listState = rememberLazyListState()

    // —— 多选态（§7.2/§22.4）——
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<EntryKey>>(emptySet()) }
    var previewEntry by remember { mutableStateOf<LinkEntry?>(null) }

    // —— 移出/转移流程（MoveFlowHolder，T-26）——
    val moveFlow = remember { MoveFlowHolder() }
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
        } else {
            val loaded = EntryParser.parse(result.content).map { LinkEntry.from(it) }
            entries = loaded
            // 重载后保留仍存在的选中项（§22.4：retainedSelection 复用——被删剔除、新插入未选）
            selectedKeys = retainedSelection(selectedKeys, loaded)
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
                    }
                )
            } else {
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
                    actions = {
                        // 多选入口（§7.1 方案 A 沿 MainScreen 先例）：浏览态常驻、条目数为 0
                        // 时隐藏（空列表无从选择）；contentDescription 直写沿惯例
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
                        readOnly = false, // 徽标正向启用（K13 反向）
                        headerText = "", // 计数已在顶栏（§22.1 headerText=null 口径）
                        selectionMode = selectionMode,
                        selectedKeys = selectedKeys,
                        onToggleSelection = { entry ->
                            // 与 MainScreen 同款勾选切换（§7.3：按 key 查 Set）
                            val key = entry.entryKey
                            selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                        },
                        onPreview = { previewEntry = it }
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

    // 多选态长按预览（§22.4——预览含复制动作，「长按复制不被占用」）
    previewEntry?.let { entry ->
        EntryPreviewDialog(entry = entry, onDismiss = { previewEntry = null })
    }
}
