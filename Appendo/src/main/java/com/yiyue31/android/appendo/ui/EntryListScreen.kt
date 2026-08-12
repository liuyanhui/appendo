package com.yiyue31.android.appendo.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.reminder.ReminderStore
import com.yiyue31.android.appendo.util.ReminderText

private const val VIBRATION_DURATION_SHORT_MS = 100L
private const val SWIPE_THRESHOLD_DP = 120f

/**
 * Reusable entry list screen component.
 *
 * NOTE: onEntrySwipeToDelete passes timestamp (not index) because:
 * - Timestamp is the unique identifier of each entry
 * - Index changes after insert/delete operations
 * - Using timestamp ensures accurate deletion regardless of list state
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryListScreen(
    entries: List<LinkEntry>,
    entryCount: Int,
    listState: LazyListState? = null,
    sourceFileName: String? = null,
    readOnly: Boolean = false,
    onEntryLongClick: (String) -> Unit = { },
    onEntrySwipeToDelete: (String) -> Unit = { },
    onEntryClick: (LinkEntry) -> Unit = { }
) {
    val context = LocalContext.current
    val reminderMap = ReminderStore.get(context).reminders.value
    val listStateResolved = listState ?: rememberLazyListState()

    // Header
    Column {
        if (sourceFileName != null) {
            Text(
                text = sourceFileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                // 按时间戳降序（决策 i）：恢复的旧时间戳条目回到历史位置，而非顶部。
                // 时间戳格式零填充 ISO 风格，字符串降序 == 时间降序。
                items = entries.sortedByDescending { it.timestamp },
                key = { index, entry -> "${entry.timestamp}_$index" }
            ) { index, entry ->
                EntryCard(
                    entry = entry,
                    index = index,
                    reminderLabel = reminderMap[entry.timestamp]?.let { m ->
                        if (!m.fired) ReminderText.fullLabel(m) else null
                    },
                    readOnly = readOnly,
                    onLongClick = {
                        performVibration(context, VIBRATION_DURATION_SHORT_MS)
                        onEntryLongClick(entry.content)
                    },
                    onSwipeToDelete = {
                        onEntrySwipeToDelete(entry.timestamp)
                    },
                    onClick = {
                        onEntryClick(entry)
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
    index: Int,
    reminderLabel: String? = null,
    readOnly: Boolean = false,
    onLongClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var offsetX by remember { mutableStateOf(0f) }
    val cardElevation by animateDpAsState(
        targetValue = if (index == 0) 8.dp else 2.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )

    // Calculate swipe action text and background
    val swipeActionText = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> "删除"
        else -> null
    }

    val cardModifier = if (readOnly) {
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { },
                onLongClick = onLongClick
            )
    } else {
        Modifier
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
                        .background(AppColors.Danger, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = swipeActionText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = cardElevation
                ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
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
                            color = AppColors.Primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Perform vibration with safety checks.
 */
private fun performVibration(context: Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(durationMs)
            }
        }
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e("EntryListScreen", "Vibration failed", e)
        }
    }
}
