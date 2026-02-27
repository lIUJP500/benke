package com.bitcat.accountbook.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()

@Composable
fun AccountBookTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        content = content
    )
}

