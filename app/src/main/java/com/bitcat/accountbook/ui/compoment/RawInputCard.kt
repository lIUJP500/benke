package com.bitcat.accountbook.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale

@Composable
fun RawInputCard(
    method: InputMethod,
    rawText: String,
    rawUri: String?,
    rawPreview: String,
    voiceRecognizing: Boolean,
    voiceLevel: Float,
    onRawTextChange: (String) -> Unit,
    onSmartParse: () -> Unit,
    onVoiceClick: () -> Unit,
    onPickPhotoClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (method) {
                InputMethod.TEXT -> {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = onRawTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        minLines = 4,
                        label = { Text("输入消费描述") },
                        placeholder = { Text("例如：午餐 25 元、打车 18.5") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                InputMethod.VOICE -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledIconButton(
                            onClick = onVoiceClick,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = if (voiceRecognizing) Icons.Filled.Stop else Icons.Filled.Mic,
                                contentDescription = if (voiceRecognizing) "停止录音" else "开始录音"
                            )
                        }
                        Text(if (voiceRecognizing) "录音中，点击停止" else "点击麦克风开始录音")
                    }
                    if (voiceRecognizing) {
                        Text("正在聆听，请说话...")
                        LinearProgressIndicator(
                            progress = { voiceLevel.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = onRawTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        minLines = 4,
                        label = { Text("语音文本（可编辑）") },
                        placeholder = { Text("语音识别结果会显示在这里") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                InputMethod.CAMERA -> {
                    Button(onClick = onTakePhotoClick, modifier = Modifier.fillMaxWidth()) {
                        Text("打开相机拍照")
                    }
                }

                InputMethod.PHOTO -> {
                    Button(onClick = onPickPhotoClick, modifier = Modifier.fillMaxWidth()) {
                        Text("从相册选择图片")
                    }
                }
            }

            if ((method == InputMethod.PHOTO || method == InputMethod.CAMERA) && !rawUri.isNullOrBlank() && looksLikeImageUri(rawUri)) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(Uri.parse(rawUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = "图片预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            }

            Button(onClick = onSmartParse, modifier = Modifier.fillMaxWidth()) {
                Text("识别")
            }
        }
    }
}

private fun looksLikeImageUri(uriStr: String): Boolean {
    val lower = uriStr.lowercase(Locale.getDefault())
    return lower.startsWith("content://") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".png") || lower.endsWith(".webp") || lower.contains("image")
}
