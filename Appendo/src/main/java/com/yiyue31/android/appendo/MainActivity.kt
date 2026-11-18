package com.yiyue31.android.appendo

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yiyue31.android.appendo.data.ArchiveFile
import com.yiyue31.android.appendo.data.ArchiveRepository
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.data.ThemePreferences
import com.yiyue31.android.appendo.ui.ArchiveDetailScreen
import com.yiyue31.android.appendo.ui.ArchiveEntryKind
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.ui.ArchiveListScreen
import com.yiyue31.android.appendo.ui.MainScreen
import com.yiyue31.android.appendo.ui.TopicListScreen
import com.yiyue31.android.appendo.ui.TopicWorkspaceScreen
import com.yiyue31.android.appendo.ui.theme.AppendoTheme
import com.yiyue31.android.appendo.ui.theme.ThemeMode
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 手动模式的冷启动窗口背景（需求 56）：须在 super（含窗口创建）之前选好主题变体；
        // SYSTEM 不动，values-night 资源系统自动解析
        ThemePreferences.applyWindowTheme(this)
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "onCreate called")
        }
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
                    currentThemeMode = themeMode.value,
                    onThemeChange = { mode ->
                        themeMode.value = mode
                        ThemePreferences.write(this, mode)
                    }
                )
            }
        }
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
    currentThemeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(
                fileRepository = fileRepository,
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

        // 主题工作区（戊批次 T-25，97，设计 §22.1）：topics 点击进入
        // （R 批次：scrollTs 深链参数随自建提醒退役删除——specs 16.C）
        composable(
            route = "topic_workspace/{filePath}",
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val filePath = Uri.decode(backStackEntry.arguments?.getString("filePath") ?: "")
            TopicWorkspaceScreen(
                filePath = filePath,
                fileRepository = fileRepository,
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
                // WB-1：时间戳/来源解析收敛 ArchiveRepository 单点（新式名带 sourceTopic，
                // 旧私有 parseTimestampFromFileName 对新式名会误兜底为打开时刻——已退役）
                val archive = androidx.compose.ui.platform.LocalContext.current.let { ctx ->
                    com.yiyue31.android.appendo.data.ArchiveRepository(ctx).archiveFileFor(file)
                } ?: ArchiveFile(file, file.name, java.util.Date(), countEntries(file))

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

private fun countEntries(file: File): Int {
    // 行为等价消重（T-01）：计数收敛到 EntryParser.countEntries（宽松正则，TD-010）；本包装保留读失败返回 0
    return try {
        EntryParser.countEntries(file.readText())
    } catch (e: Exception) {
        0
    }
}
