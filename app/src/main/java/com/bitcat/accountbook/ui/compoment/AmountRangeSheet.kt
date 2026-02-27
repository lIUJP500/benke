package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
//
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountRangeSheet(
    minText: String,
    maxText: String,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("金额区间", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = minText,
                onValueChange = onMinChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("最低金额（可空）") },
                prefix = { Text("¥") }
            )

            OutlinedTextField(
                value = maxText,
                onValueChange = onMaxChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("最高金额（可空）") },
                prefix = { Text("¥") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("确定") }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
