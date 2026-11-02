package com.yiyue31.android.appendo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.yiyue31.android.appendo.util.EntryKey

/**
 * 「移入主题」多选模式基础设施（topic-move 设计 §7）：
 * - [entryKey]：[LinkEntry] → [EntryKey] 扩展（§6.2 注：放 ui 层；勾选、事务寻址、目标去重同键复用）；
 * - [retainedSelection]：选中集维护纯函数（§7.3，82 修订 a，可 JVM 单测）；
 * - [MultiSelectTopBar]：多选态 contextual 顶栏（§7.2 五要素之三：关闭 ×／已选 N 条／移入）。
 */

/** 条目 [EntryKey]（timestamp|content 组合键，§7.3）：由 UI 层条目模型的同名字段构造。 */
val LinkEntry.entryKey: EntryKey
    get() = EntryKey(timestamp, content)

/**
 * 轮询/并发插入刷新后保留仍存在的选中项（§7.3）：
 * 条目仍在 → 保持勾选；已被删除 → 剔除（不丢失、不错位）；新插入条目默认未选。
 * 列表渲染勾选态按 key 查 Set——与 LazyColumn 复合 key（"${timestamp}_$index"）解耦；
 * 同 key 双条（同 ts 同 content）勾选态联动，可接受（设计 §12 注记）。
 */
fun retainedSelection(selectedKeys: Set<EntryKey>, currentEntries: List<LinkEntry>): Set<EntryKey> {
    val presentKeys = currentEntries.mapTo(mutableSetOf()) { it.entryKey }
    return selectedKeys.filterTo(mutableSetOf()) { it in presentKeys }
}

/**
 * 多选态 contextual TopAppBar（§7.2）：顶栏整体切换——浏览态主标题「Appendo/已收集 N 条」
 * 副标题由此自然隐藏。
 * - NavigationIcon：关闭 ×（[onExit] 退出并清空选中集）；
 * - 标题：「已选 N 条」（N=[selectedCount] 实时，§9.2）；
 * - actions：「移入」TextButton（N ≥ 1 启用，primary；[onMoveIn] 由 T-06 接移入流程）。
 *
 * contentDescription「退出多选」直写：沿 MainScreen/ArchiveDetailScreen 既有 contentDescription
 * 直写惯例（「菜单」「返回」同款），字面为 §9.2 定稿。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiSelectTopBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onMoveIn: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, contentDescription = "退出多选")
            }
        },
        title = {
            Text(
                Microcopy.topBarSelectedCount(selectedCount),
                fontWeight = FontWeight.SemiBold
            )
        },
        actions = {
            TextButton(
                onClick = onMoveIn,
                enabled = selectedCount >= 1, // §7.2：N ≥ 1 启用
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(Microcopy.BTN_MOVE_IN)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
