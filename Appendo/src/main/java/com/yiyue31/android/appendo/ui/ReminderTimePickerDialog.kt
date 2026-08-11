package com.yiyue31.android.appendo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * 微信式提醒时间选择：预设网格（2 列）+ 自定义（日期 → 时间）。[onPick] 回传触发时刻（epoch 毫秒）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimePickerDialog(
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableStateOf(Stage.MAIN) }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    val nowMs = remember { System.currentTimeMillis() }

    fun atDaysAhead(hour: Int, minute: Int, daysAhead: Int): Long =
        Calendar.getInstance().apply {
            add(Calendar.DATE, daysAhead)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    data class Preset(val label: String, val triggerAt: Long)
    val presets = remember(nowMs) {
        listOf(
            Preset("1 小时后", nowMs + 3_600_000L),
            Preset("2 小时后", nowMs + 7_200_000L),
            Preset("明天 9:00", atDaysAhead(9, 0, 1)),
            Preset("后天 9:00", atDaysAhead(9, 0, 2)),
            Preset("3 天后 9:00", atDaysAhead(9, 0, 3)),
            Preset("一周后", nowMs + 7L * 24 * 3_600_000L)
        )
    }

    when (stage) {
        Stage.MAIN -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("设置提醒") },
            text = {
                Column {
                    presets.chunked(2).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { p ->
                                OutlinedButton(
                                    onClick = { onPick(p.triggerAt) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(p.label) }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = { stage = Stage.DATE },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("自定义日期时间…") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
        )

        Stage.DATE -> {
            val dpState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        val d = dpState.selectedDateMillis
                        if (d != null) { dateMillis = d; stage = Stage.TIME }
                    }) { Text("下一步") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
            ) { DatePicker(state = dpState) }
        }

        Stage.TIME -> {
            val now = Calendar.getInstance()
            val tpState = rememberTimePickerState(
                initialHour = now.get(Calendar.HOUR_OF_DAY),
                initialMinute = now.get(Calendar.MINUTE),
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("选择时间") },
                text = { TimePicker(state = tpState) },
                confirmButton = {
                    TextButton(onClick = {
                        val d = dateMillis ?: return@TextButton
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = d
                            set(Calendar.HOUR_OF_DAY, tpState.hour)
                            set(Calendar.MINUTE, tpState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onPick(cal.timeInMillis)
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
            )
        }
    }
}

private enum class Stage { MAIN, DATE, TIME }
