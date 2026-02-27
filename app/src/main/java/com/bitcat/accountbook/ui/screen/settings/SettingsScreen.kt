package com.bitcat.accountbook.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitcat.accountbook.data.database.DatabaseProvider
import com.bitcat.accountbook.data.datastore.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    val settings = remember { SettingsDataStore(appContext) }
    val retentionDays by settings.rawRetentionDays.collectAsStateWithLifecycle(initialValue = 30)

    val db = remember { DatabaseProvider.get(appContext) }
    val rawDao = remember { db.rawInputDao() }

    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    var info by rememberSaveable { mutableStateOf("") }

    // ✅ 当 DataStore 的值变化时，同步输入框（但你也可以加“用户正在编辑时不覆盖”的逻辑）
    LaunchedEffect(retentionDays) {
        input = retentionDays.toString()
    }

    Card(modifier) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("原始记录清理", style = MaterialTheme.typography.titleMedium)

            Text(
                "为节省存储空间，系统会自动删除超过保留时长的原始输入（raw_inputs），但不会删除结构化消费记录（records）。",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = { Text("原始记录保留天数") },
                supportingText = { Text("建议 7～90 天，默认 30 天") },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val days = input.toIntOrNull()
                        if (days == null || days <= 0) {
                            info = "请输入有效天数（>0）"
                            return@Button
                        }
                        scope.launch {
                            settings.setRawRetentionDays(days)
                            info = "已保存：保留 $days 天"
                        }
                    }
                ) { Text("保存") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val days = retentionDays.coerceAtLeast(1)
                            val threshold = System.currentTimeMillis() -
                                    TimeUnit.DAYS.toMillis(days.toLong())

                            val deleted = withContext(Dispatchers.IO) {
                                rawDao.deleteBefore(threshold)
                            }

                            // ✅ 回到主线程更新 UI 状态
                            info = "已清理：删除 $deleted 条原始输入"
                        }
                    }
                ) { Text("立即清理") }
            }

            if (info.isNotBlank()) {
                Text(info, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
