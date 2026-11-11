package com.yiyue31.android.appendo.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yiyue31.android.appendo.BuildConfig
import com.yiyue31.android.appendo.ui.theme.onSuccessColor
import com.yiyue31.android.appendo.ui.theme.successColor

// Constants for magic numbers（随骨架自 ArchiveListScreen 下沉，T-13；振动时长为三消费方共享常量）
internal const val VIBRATION_DURATION_SHORT_MS = 100L
internal const val VIBRATION_DURATION_LONG_MS = 200L

private const val SWIPE_THRESHOLD_DP = 120f

/**
 * 滑动手势骨架（T-13，设计修订二 §14.1/§15.1 A8 裁定）：自 ArchiveCard 下沉的共用组件——
 * offset 状态、120dp 阈值（SWIPE_THRESHOLD_DP）、方向背景指示（右滑「删除」error 红／
 * 左滑「追加」successColor 绿＋onError/onSuccessColor 文字色）、禁删钳 0
 * （[swipeToDeleteEnabled] = false 时正向位移钳 0，85(a) 零响应）、行为回调注入。
 *
 * 卡片平面化（v1.4 换机验证视觉裁定，参考 iOS 分组列表）：elevation 恒 0、无首卡特例——
 * 层级由页面底色（colorScheme.background）与卡片 surface 的色调差呈现，全应用列表统一。
 * 指示字面「删除/追加」与配色随骨架下沉、**不参数化**（两页语义同构：删除红/恢复绿，设计裁定）。
 * 既有恒真 `AnimatedVisibility(visible = true)` 包装随重构丢弃（A-6 后置项：恒真包装无动画语义）。
 * 消费方：ArchiveListScreen.ArchiveCard（薄包装）；T-14 TopicListScreen.TopicCard（薄包装）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwipeCard(
    swipeToDeleteEnabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeToDelete: () -> Unit,
    onSwipeLeftRestore: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }

    // Calculate swipe action text and background
    val swipeActionText = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> "删除"
        offsetX < -SWIPE_THRESHOLD_DP / 2 -> "追加"
        else -> null
    }

    val swipeBackgroundColor = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.colorScheme.error // Red for delete
        offsetX < -SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.successColor // Green for restore
        else -> Color.Transparent
    }

    // 深色主题下容器为亮色变体，文字用深色 onXxx（需求 51/设计 §4）
    val swipeContentColor = when {
        offsetX > SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.colorScheme.onError
        offsetX < -SWIPE_THRESHOLD_DP / 2 -> MaterialTheme.onSuccessColor
        else -> Color.White
    }

    Box {
        // Background layer for swipe action indicator
        if (swipeActionText != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(swipeBackgroundColor, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = when {
                    offsetX > 0 -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                }
            ) {
                Text(
                    text = swipeActionText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = swipeContentColor
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.toInt(), 0) }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        onClick()
                    },
                    onLongClick = {
                        onLongClick()
                    }
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > SWIPE_THRESHOLD_DP) {
                                onSwipeToDelete()
                            } else if (offsetX < -SWIPE_THRESHOLD_DP) {
                                onSwipeLeftRestore()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            // Allow both positive and negative offset
                            // 右滑删除禁用时（非空主题，85(a) 零响应）：正向位移钳 0——卡片无滑动反馈，
                            // onDragEnd 的 > 阈值分支不可达；负向（左滑恢复）不受限
                            val maxPositive = if (swipeToDeleteEnabled) SWIPE_THRESHOLD_DP * 1.5f else 0f
                            offsetX = newOffset.coerceIn(-SWIPE_THRESHOLD_DP * 1.5f, maxPositive)
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            // 平面卡（v1.4 视觉裁定）：elevation 恒 0，靠底色分层
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            content()
        }
    }
}

/**
 * Perform vibration with safety checks（T-13 随骨架下沉为 internal 共享，A-4/E-3 三方收敛：
 * 原三处私有拷贝——ArchiveListScreen/EntryListScreen/MainScreen——删除改引用本件）.
 * Checks hasVibrator() before vibrating.
 */
internal fun performVibration(context: Context, durationMs: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Check if vibration is supported
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
            android.util.Log.e("SwipeCard", "Vibration failed", e)
        }
        // Ignore vibration errors - not critical
    }
}
