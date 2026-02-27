package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 原始输入+智能解析
@Composable
fun RawInputCard(
    method: InputMethod,
    rawText: String,
    onRawTextChange: (String) -> Unit,
    onSmartParse: () -> Unit,
    onVoiceClick: () -> Unit,
    onPickPhotoClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("原始输入", style = MaterialTheme.typography.titleMedium)

            when (method) {
                InputMethod.TEXT -> {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = onRawTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("输入消费描述（例：2/10 晚餐 38.5 元）") }
                    )
                }

                InputMethod.VOICE -> {
                    Text("语音输入")
                    Button(onClick = onVoiceClick, modifier = Modifier.fillMaxWidth()) {
                        Text("开始语音识别")
                    }
                }

                InputMethod.CAMERA -> {
                    Text("拍照输入")
                    Button(onClick = onTakePhotoClick, modifier = Modifier.fillMaxWidth()) {
                        Text("打开相机拍照")
                    }
                }

                InputMethod.PHOTO -> {
                    Text("上传图片")
                    Button(onClick = onPickPhotoClick, modifier = Modifier.fillMaxWidth()) {
                        Text("从相册选择图片")
                    }
                }
            }

            Button(onClick = onSmartParse, modifier = Modifier.fillMaxWidth()) {
                Text("智能解析")
            }
        }
    }
}

