package com.bitcat.accountbook.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onRawTextChange: (String) -> Unit,
    onSmartParse: () -> Unit,
    onVoiceClick: () -> Unit,
    onPickPhotoClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (method) {
                InputMethod.TEXT -> {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = onRawTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("输入消费描述（例：5/10 晚饭 38.5 元）") }
                    )
                }

                InputMethod.VOICE -> {
                    Button(
                        onClick = onVoiceClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !voiceRecognizing
                    ) {
                        Text(if (voiceRecognizing) "正在语音识别..." else "开始语音识别")
                    }
                    if (rawPreview.isNotBlank()) {
                        Text(rawPreview)
                    }
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
                Text("智能解析")
            }
        }
    }
}

private fun looksLikeImageUri(uriStr: String): Boolean {
    val lower = uriStr.lowercase(Locale.getDefault())
    return lower.startsWith("content://") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".png") || lower.endsWith(".webp") || lower.contains("image")
}
