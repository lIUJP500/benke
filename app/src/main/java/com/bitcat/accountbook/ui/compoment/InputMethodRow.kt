package com.bitcat.accountbook.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
//  输入方法选择
enum class InputMethod { TEXT, VOICE, PHOTO, CAMERA  }

@Composable
fun InputMethodRow(
    selected: InputMethod,
    onSelect: (InputMethod) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == InputMethod.TEXT,
            onClick = { onSelect(InputMethod.TEXT) },
            label = { Text("文本") }
        )
        FilterChip(
            selected = selected == InputMethod.VOICE,
            onClick = { onSelect(InputMethod.VOICE) },
            label = { Text("语音") }
        )
        FilterChip(
            selected = selected == InputMethod.CAMERA,
            onClick = { onSelect(InputMethod.CAMERA) },
            label = { Text("拍照") }
        )
        FilterChip(
            selected = selected == InputMethod.PHOTO,
            onClick = { onSelect(InputMethod.PHOTO) },
            label = { Text("上传图片") }
        )
    }
}
