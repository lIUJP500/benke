package com.bitcat.accountbook.ui.component

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun BudgetWarningDialog(
    title: String,
    message: String,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onClose) { Text("知道了") } }
    )
}
