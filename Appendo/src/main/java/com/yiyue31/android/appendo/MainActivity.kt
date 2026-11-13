package com.yiyue31.android.appendo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yiyue31.android.appendo.data.ArchiveFile
import com.yiyue31.android.appendo.data.ArchiveRepository
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.ThemePreferences
import com.yiyue31.android.appendo.reminder.EntryLocation
import com.yiyue31.android.appendo.reminder.EntryLocator
import com.yiyue31.android.appendo.reminder.ReminderIntents
import com.yiyue31.android.appendo.ui.ArchiveDetailScreen
import com.yiyue31.android.appendo.ui.ArchiveEntryKind
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.ui.ArchiveListScreen
import com.yiyue31.android.appendo.ui.MainScreen
import com.yiyue31.android.appendo.ui.TopicListScreen
import com.yiyue31.android.appendo.ui.TopicWorkspaceScreen
import com.yiyue31.android.appendo.ui.theme.AppendoTheme
import com.yiyue31.android.appendo.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    /** 来自提醒通知点按的"待滚动到"记录时间戳（深链）。 */
    private val scrollTs = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 手动模式的冷启动窗口背景（需求 56）：须在 super（含窗口创建）之前选好主题变体；
        // SYSTEM 不动，values-night 资源系统自动解析
        ThemePreferences.applyWindowTheme(this)
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "onCreate called")
        }
        scrollTs.value = intent?.getStringExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP)
        val fileRepository = FileRepository(this)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "FileRepository created, URI: ${fileRepository.getFileUri()}")
        }
        // 手动切换主题 = 改这个 State（纯重组生效，无 Activity 重建，导航栈/滚动不动——需求 50/53）
        val themeMode = mutableStateOf(ThemePreferences.read(this))
        setContent {
            AppendoTheme(themeMode.value) {
                AppendoNavHost(
                    fileRepository,
                    scrollTs,
                    onConsumeScrollTs = { scrollTs.value = null },
                    currentThemeMode = themeMode.value,
                    onThemeChange = { mode ->
                        themeMode.value = mode
                        ThemePreferences.write(this, mode)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        scrollTs.value = intent.getStringExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP)
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "onResume called")
        }
    }
}

@Composable
fun AppendoNavHost(
    fileRepository: FileRepository,
    scrollTs: State<String?>,
    onConsumeScrollTs: () -> Unit = {},
    currentThemeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 深链解析（95(c)，设计 §20.4 评审 C1：navController 在本组合内创建——解析与导航落组合域）：
    // IO 调 EntryLocator.locateEntry（读文件不进主线程）；MainFile→不动作（MainScreen 既有
    // LaunchedEffect 消费，scrolledTs 只滚一次护栏自守）；Topic→门控「当前目的地＝main」
    // （不满足＝用户在其他页，丢弃不弹错）→ navigate 工作区（scrollTs 透传定位）；
    // null（已删/读失败）→清空 State、不跳不弹错（architecture.md §5.6 口径）。
    // Topic 态（导航或门控丢弃）与 miss 均消费清空——防返回主列表时同 ts 二次消费（§20.4）
    LaunchedEffect(scrollTs.value) {
        val ts = scrollTs.value ?: return@LaunchedEffect
        when (val location = withContext(Dispatchers.IO) { EntryLocator.locateEntry(context, ts) }) {
            EntryLocation.MainFile -> Unit // MainScreen 侧消费，此处不清空（双通道一致性，§20.4）
            is EntryLocation.Topic -> {
                val atMain =
                    navController.currentBackStackEntry?.destination?.route == "main"
                if (atMain) {
                    // ts 含空格/点——查询参数整体 encode（读取侧对称 decode）
                    navController.navigate(
                        "topic_workspace/${Uri.encode(location.file.absolutePath)}" +
                            "?scrollTs=${Uri.encode(ts)}"
                    )
                }
                onConsumeScrollTs()
            }
            null -> onConsumeScrollTs()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                fileRepository = fileRepository,
                scrollToTs = scrollTs.value,
                onNavigateToArchiveList = {
                    navController.navigate("archive_list")
                },
                onNavigateToTopics = {
                    navController.navigate("topics")
                },
                currentThemeMode = currentThemeMode,
                onThemeChange = onThemeChange
            )
        }

        // 主题独立页（修订二 §14.2 路由图，T-14）：main 顶栏「主题」入口进入
        composable("topics") {
            TopicListScreen(
                fileRepository = fileRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTopicClick = { file ->
                    // 主题工作区改道（戊批次 T-25，97：archive_detail 主题路径随 T-30 退役）：
                    // filePath 经 Uri.encode（沿既有调用点构造式）
                    navController.navigate("topic_workspace/${Uri.encode(file.absolutePath)}")
                }
            )
        }

        // 主题工作区（戊批次 T-25，97，设计 §22.1）：topics 点击进入；深链携带 scrollTs 定位
        composable(
            route = "topic_workspace/{filePath}?scrollTs={scrollTs}",
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType },
                navArgument("scrollTs") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val filePath = Uri.decode(backStackEntry.arguments?.getString("filePath") ?: "")
            TopicWorkspaceScreen(
                filePath = filePath,
                fileRepository = fileRepository,
                scrollToTs = backStackEntry.arguments?.getString("scrollTs")?.let { Uri.decode(it) },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("archive_list") {
            ArchiveListScreen(
                fileRepository = fileRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onArchiveClick = { file ->
                    // Encode file path for safe navigation
                    val encodedPath = Uri.encode(file.absolutePath)
                    // 修订二 §15.2：kind 恒归档（不带 kind 查询参数，路由匹配向后兼容，§8.5）
                    navController.navigate("archive_detail/$encodedPath")
                }
            )
        }

        composable(
            route = "archive_detail/{filePath}?kind={kind}",
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType },
                navArgument("kind") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath")
            val filePath = encodedPath?.let { Uri.decode(it) }
            val kind = ArchiveEntryKind.fromRouteValue(backStackEntry.arguments?.getString("kind"))
            val file = File(filePath ?: "")

            if (file.exists()) {
                val timestamp = parseTimestampFromFileName(file.name)
                val entryCount = countEntries(file)
                val archive = ArchiveFile(file, file.name, timestamp ?: java.util.Date(), entryCount)

                ArchiveDetailScreen(
                    archive = archive,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    screenTitle = if (kind == ArchiveEntryKind.TOPIC) "主题详情" else "归档详情",
                    kind = kind
                )
            }
        }
    }
}

private fun parseTimestampFromFileName(filename: String): java.util.Date? {
    return try {
        val timestampStr = filename
            .removePrefix("Appendo_")
            .removeSuffix(".md")
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).parse(timestampStr)
    } catch (e: Exception) {
        null
    }
}

private fun countEntries(file: File): Int {
    // 行为等价消重（T-01）：计数收敛到 EntryParser.countEntries（宽松正则，TD-010）；本包装保留读失败返回 0
    return try {
        EntryParser.countEntries(file.readText())
    } catch (e: Exception) {
        0
    }
}
