package com.bitcat.accountbook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bitcat.accountbook.ui.screen.add.AddRecordScreen
import com.bitcat.accountbook.ui.screen.list.RecordListScreen
import com.bitcat.accountbook.ui.screen.settings.SettingsScreen
import com.bitcat.accountbook.ui.screen.stats.StatisticsScreen
import com.bitcat.accountbook.ui.screen.detail.RecordDetailScreen
@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppDestination.Add.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        composable(AppDestination.Add.route) {
            AddRecordScreen(navController)
        }

        composable(AppDestination.List.route) {
            RecordListScreen(navController)
        }

        composable(AppDestination.Stats.route) { StatisticsScreen() }
        composable(AppDestination.Settings.route) { SettingsScreen() }


        composable("detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments
                ?.getString("id")
                ?.toLongOrNull()
                ?: return@composable

            RecordDetailScreen(
                recordId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate("edit/$it") }
            )
        }
        composable("edit/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
            com.bitcat.accountbook.ui.screen.edit.EditRecordScreen(
                recordId = id,
                onBack = { navController.popBackStack() }
            )
        }


    }

}

