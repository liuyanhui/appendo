package com.yiyue31.android.appendo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.reminder.ReminderStore
import com.yiyue31.android.appendo.util.EntryKey
import com.yiyue31.android.appendo.util.ReminderText

// 振动时长常量与 performVibration 已随 SwipeCard 骨架共享下沉（T-13，A-4/E-3），本文件直接引用
private const val SWIPE_THRESHOLD_DP = 120f

/**
 * Reusable entry list screen component.
 *
 * NOTE: onEntrySwipeToDelete passes timestamp (not index) because:
 * - Timestamp is the unique identifier of each entry
 * - Index changes after insert/delete operations
 * - Using timestamp ensures accurate deletion regardless of list state
 *
 * 多选模式（topic-move 设计 §7.2，T-05）：[selectionMode] = true 时点击卡片＝切换勾选
 * （[onToggleSelection]）、长按＝预览回调（[onPreview]，对话框本体 T-06 交付）、滑动删除禁用；
 * 勾选态按 [entryKey] 查 [selectedKeys]（§7.3，与 LazyColumn 复合 key 解耦）。
 * 四个新参数均带默认值——既有调用点（浏览态 MainScreen、ArchiveDetailScreen）零改动。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryListScreen(
    entries: List<LinkEntry>,
    entryCount: Int,
    listState: LazyListState? = null,
    readOnly: Boolean = false,
    onEntryLongClick: (String) -> Unit = { },
    onEntrySwipeToDelete: (String) -> Unit = { },
    onEntryClick: (LinkEntry) -> Unit = { },
    selectionMode: Boolean = false,
    selectedKeys: Set<EntryKey> = emptySet(),
    onToggleSelection: (LinkEntry) -> Unit = { },
    onPreview: (LinkEntry) -> Unit = { }
) {
    val context = LocalContext.current
    val reminderMap = ReminderStore.get(context).reminders.value
    val listStateResolved = listState ?: rememberLazyListState()

    // Header（sourceFileName 参数随 2026-11-11 主题名并入顶栏标题裁定退役）
    Column {
        Text(
            "已收集 $entryCount 条",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            state = listStateResolved,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                // 按时间戳降序（决策 i）：恢复的旧时间戳条目回到历史位置，而非顶部。
                // 时间戳格式零填充 ISO 风格，字符串降序 == 时间降序。
                items = entries.sortedByDescending { it.timestamp },
                key = { index, entry -> "${entry.timestamp}_$index" }
            ) { _, entry ->
                EntryCard(
                    entry = entry,
                    // 归档详情（readOnly）不显示提醒徽标：归档是历史快照，提醒属于活动文件的同时间戳条目（缺陷① 2026-09-02）
                    reminderLabel = if (readOnly) null else reminderMap[entry.timestamp]?.let { m ->
                        if (!m.fired) ReminderText.fullLabel(m) else null
                    },
                    readOnly = readOnly,
                    // §7.3：勾选态按 key 查 Set——与复合 key（"${timestamp}_$index"）解耦，插删后不错位
                    selectionMode = selectionMode,
                    isSelected = selectionMode && entry.entryKey in selectedKeys,
                    onLongClick = {
                        performVibration(context, VIBRATION_DURATION_SHORT_MS)
                        onEntryLongClick(entry.content)
                    },
                    onSwipeToDelete = {
                        onEntrySwipeToDelete(entry.timestamp)
                    },
                    onClick = {
                        onEntryClick(entry)
                    },
                    onToggleSelection = {
                        onToggleSelection(entry)
                    },
                    onPreview = {
                        // §7.4：多选态长按＝预览全文；触感反馈与浏览态长按一致
                        performVibration(context, VIBRATION_DURATION_SHORT_MS)
                        onPreview(entry)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: LinkEntry,
    reminderLabel: String? = null,
    readOnly: Boolean = false,
    onLongClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
    onClick: () -> Unit = {},
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onPreview: () -> Unit = {}
) {
    val context = LocalContext.current
    var offsetX by remember { mutableStateOf(0f) }

    // Calculate swipe action text and background
    val swipeActionText = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> "删除"
        else -> null
    }

    val cardModifier = when {
        // 多选态（§7.2）：点击＝切换勾选、长按＝预览；滑动删除禁用（无拖拽手势与位移）
        selectionMode -> Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleSelection,
                onLongClick = onPreview
            )
        readOnly -> Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { },
                onLongClick = onLongClick
            )
        else -> Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.toInt(), 0) }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick() },
                onLongClick = onLongClick
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > SWIPE_THRESHOLD_DP) {
                            onSwipeToDelete()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val newOffset = offsetX + dragAmount
                        if (newOffset > 0) {
                            offsetX = newOffset.coerceAtMost(SWIPE_THRESHOLD_DP * 1.5f)
                        }
                    }
                )
            }
    }

    // §7.2/P4：选中卡片容器色 surface → surfaceVariant 轻变（spring Medium，与勾选缩放同节奏，无装饰动效）
    val containerColor by animateColorAsState(
        targetValue = if (selectionMode && isSelected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "selectContainerColor"
    )

    AnimatedVisibility(
        visible = true,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ),
        exit = shrinkVertically() + fadeOut()
    ) {
        Box {
            // Background layer for swipe action indicator
            if (swipeActionText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = swipeActionText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }

            Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                // 平面卡（v1.4 视觉裁定）：elevation 恒 0，靠底色分层
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = containerColor
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (selectionMode) {
                        // 卡片左缘圆形勾选框（§7.2，primary；正文/时间戳层级不变，P3）
                        SelectIndicator(selected = isSelected)
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.timestampDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = entry.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (reminderLabel != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⏰ $reminderLabel 提醒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 多选态卡片左缘圆形勾选框（§7.2）：选中＝primary 实心圆＋onPrimary 勾（spring Medium 缩放入场，
 * P4 动效只解释状态变化）；未选＝onSurfaceVariant 描边空圆。颜色全部经 colorScheme（P6/P7）。
 */
@Composable
private fun SelectIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "selectCheckScale"
    )
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = if (selected) 2.dp else 1.5.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                )
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                )
        )
        if (checkScale > 0.01f) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(14.dp)
                    .scale(checkScale)
            )
        }
    }
}
