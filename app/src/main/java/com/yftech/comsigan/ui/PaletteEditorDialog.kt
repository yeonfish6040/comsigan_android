package com.yftech.comsigan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.data.ColorSlot
import com.yftech.comsigan.data.CustomPalette
import com.yftech.comsigan.data.CustomPalettes
import com.yftech.comsigan.widget.CustomWidgetTheme

/** 체크무늬 — 알파가 있는 색을 볼 때 배경으로 깐다. */
private val CheckerBrush = Brush.linearGradient(listOf(Color(0xFFBDBDBD), Color(0xFFEFEFEF)))

/**
 * 사용자 정의 팔레트를 만들고 고친다.
 * 색 목록은 [CustomPalettes.slots]를 그대로 훑으므로, 색이 늘어나면 여기도 자동으로 늘어난다.
 */
@Composable
fun PaletteEditorDialog(
    initial: CustomPalette,
    onDismiss: () -> Unit,
    onSave: (CustomPalette) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var palette by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<ColorSlot?>(null) }

    editing?.let { slot ->
        ColorPickerDialog(
            title = slot.label,
            initial = slot.get(palette.colors),
            onDismiss = { editing = null },
            onPick = { color ->
                palette = palette.copy(colors = slot.set(palette.colors, color))
                editing = null
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("팔레트 편집") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 고치는 즉시 위젯과 같은 구성으로 미리 보여준다.
                WidgetThemePreview(
                    theme = CustomWidgetTheme(palette),
                    selected = false,
                    onClick = {},
                )

                OutlinedTextField(
                    value = palette.name,
                    onValueChange = { palette = palette.copy(name = it) },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("배경이 비치는 테마", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = palette.translucent,
                        onCheckedChange = { palette = palette.copy(translucent = it) },
                    )
                }

                CustomPalettes.slots.forEach { slot ->
                    ColorRow(
                        label = slot.label,
                        color = slot.get(palette.colors),
                        onHexChange = { text ->
                            CustomPalettes.parseRgba(text)?.let {
                                palette = palette.copy(colors = slot.set(palette.colors, it))
                            }
                        },
                        onOpenPicker = { editing = slot },
                    )
                }

                Text(
                    "색은 #RRGGBBAA(rgba) 표기입니다. 끝 두 자리가 투명도예요.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(palette) }) { Text("저장") } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("삭제") } }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

@Composable
private fun ColorRow(
    label: String,
    color: Color,
    onHexChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
) {
    var text by remember(color) { mutableStateOf(CustomPalettes.formatRgba(color)) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch(color, Modifier.size(34.dp).clickable { onOpenPicker() })
        Text(label, fontSize = 12.sp, modifier = Modifier.width(88.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onHexChange(it)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Swatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CheckerBrush)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp).background(color))
    }
}

/** RGBA 슬라이더 컬러 피커. */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Color,
    onDismiss: () -> Unit,
    onPick: (Color) -> Unit,
) {
    var red by remember { mutableStateOf(initial.red * 255f) }
    var green by remember { mutableStateOf(initial.green * 255f) }
    var blue by remember { mutableStateOf(initial.blue * 255f) }
    var alpha by remember { mutableStateOf(initial.alpha * 255f) }

    val color = Color(
        red = red.toInt().coerceIn(0, 255),
        green = green.toInt().coerceIn(0, 255),
        blue = blue.toInt().coerceIn(0, 255),
        alpha = alpha.toInt().coerceIn(0, 255),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Swatch(color, Modifier.fillMaxWidth().height(56.dp))
                Text(
                    CustomPalettes.formatRgba(color),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                ChannelSlider("R", red, Color(0xFFD32F2F)) { red = it }
                ChannelSlider("G", green, Color(0xFF388E3C)) { green = it }
                ChannelSlider("B", blue, Color(0xFF1976D2)) { blue = it }
                ChannelSlider("A", alpha, MaterialTheme.colorScheme.outline) { alpha = it }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("선택") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ChannelSlider(label: String, value: Float, tint: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.width(16.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            value.toInt().toString(),
            fontSize = 12.sp,
            modifier = Modifier.width(34.dp),
        )
    }
}
