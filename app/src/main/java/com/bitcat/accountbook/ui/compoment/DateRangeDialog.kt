package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

//日期范围（对话框占位）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (startUtcMillis: Long, endUtcMillisInclusive: Long) -> Unit
) {
    val state = rememberDateRangePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null &&
                        state.selectedEndDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis!!
                    val end = state.selectedEndDateMillis!!

                    val endInclusive =
                        end + (24L * 60 * 60 * 1000) - 1

                    onConfirm(start, endInclusive)
                }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("选择日期范围") },
        text = {
            DateRangePicker(
                state = state,
                showModeToggle = false
            )
        }
    )
}
