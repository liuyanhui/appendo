package com.yiyue31.android.appendo.ui

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * N-4（TD-051）恢复开态安全聚焦：配置变更重建时对话框经 rememberSaveable 恢复重组，
 * LaunchedEffect(Unit) 可能早于对话框窗口/focus 节点挂载触发 requestFocus →
 * IllegalStateException 崩溃（真机实证 2026-11-20，TC-100 首测）——等一帧让 focus 目标就绪，
 * 再以 runCatching 让过仍未就绪的恢复帧竞态（自动聚焦/弹键盘＝体验增强非硬需求，
 * 正常打开路径不受影响）。
 */
internal suspend fun FocusRequester.requestFocusSafe() {
    withFrameNanos { } // 恢复帧竞态：等对话框内容挂载完成
    runCatching { requestFocus() }
}