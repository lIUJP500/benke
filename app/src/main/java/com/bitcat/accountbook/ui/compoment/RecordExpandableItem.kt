package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RecordExpandableItem(
    dateText: String,
    title: String,
    amountText: String,
    tags: List<String>,
    rawInputPreview: String
) {
    var expanded by remember { mutableStateOf(false) }

    Card {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(dateText, style = MaterialTheme.typography.bodySmall)
                }
                Text(amountText, style = MaterialTheme.typography.titleMedium)
            }

            if (tags.isNotEmpty()) {
                Text("标签：${tags.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起原始输入" else "查看原始输入")
            }
            if (expanded) {
                Text(rawInputPreview, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
