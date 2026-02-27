package com.bitcat.accountbook.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Add : AppDestination("add", "记账", Icons.Filled.AddCircle)
    data object List : AppDestination("list", "记录", Icons.AutoMirrored.Filled.List)
    data object Stats : AppDestination("stats", "统计", Icons.AutoMirrored.Filled.ShowChart)
    data object Settings : AppDestination("settings", "设置", Icons.Filled.Settings)
}

val bottomDestinations = listOf(
    AppDestination.Add,
    AppDestination.List,
    AppDestination.Stats,
    AppDestination.Settings
)
