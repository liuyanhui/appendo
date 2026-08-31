package com.yiyue31.android.appendo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yiyue31.android.appendo.data.ArchiveFile
import com.yiyue31.android.appendo.data.ArchiveRepository
import com.yiyue31.android.appendo.data.FileRepository
import com.yiyue31.android.appendo.reminder.ReminderIntents
import com.yiyue31.android.appendo.ui.ArchiveDetailScreen
import com.yiyue31.android.appendo.util.EntryParser
import com.yiyue31.android.appendo.ui.ArchiveListScreen
import com.yiyue31.android.appendo.ui.MainScreen
import java.io.File

class MainActivity : ComponentActivity() {

    /** 来自提醒通知点按的"待滚动到"记录时间戳（深链）。 */
    private val scrollTs = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "onCreate called")
        }
        scrollTs.value = intent?.getStringExtra(ReminderIntents.EXTRA_ENTRY_TIMESTAMP)
        val fileRepository = FileRepository(this)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "FileRepository created, URI: ${fileRepository.getFileUri()}")
        }
        setContent {
            AppendoNavHost(fileRepository, scrollTs)
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
fun AppendoNavHost(fileRepository: FileRepository, scrollTs: State<String?>) {
    val navController = rememberNavController()

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
                }
            )
        }

        composable("archive_list") {
            ArchiveListScreen(
                fileRepository = fileRepository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onArchiveClick = { archive ->
                    // Encode file path for safe navigation
                    val encodedPath = Uri.encode(archive.file.absolutePath)
                    navController.navigate("archive_detail/$encodedPath")
                }
            )
        }

        composable(
            route = "archive_detail/{filePath}",
            arguments = listOf(navArgument("filePath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("filePath")
            val filePath = encodedPath?.let { Uri.decode(it) }
            val file = File(filePath ?: "")

            if (file.exists()) {
                val timestamp = parseTimestampFromFileName(file.name)
                val entryCount = countEntries(file)
                val archive = ArchiveFile(file, file.name, timestamp ?: java.util.Date(), entryCount)

                ArchiveDetailScreen(
                    archive = archive,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
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
    return try {
        // 宽松正则（TD-010）：秒级/毫秒级条目都数得到，与 EntryParser 同源
        EntryParser.getTimestampRegex().findAll(file.readText()).count()
    } catch (e: Exception) {
        0
    }
}
