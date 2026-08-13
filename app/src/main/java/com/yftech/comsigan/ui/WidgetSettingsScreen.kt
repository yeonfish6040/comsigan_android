package com.yftech.comsigan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.data.AppSettings
import com.yftech.comsigan.data.CustomPalette
import com.yftech.comsigan.data.CustomPalettes
import com.yftech.comsigan.widget.CustomWidgetTheme
import com.yftech.comsigan.widget.WidgetLayouts
import com.yftech.comsigan.widget.WidgetSlot
import com.yftech.comsigan.widget.WidgetThemes

/**
 * 위젯 테마와 자리별 레이아웃을 고르는 화면.
 * 목록은 WidgetThemes.all / WidgetLayouts.all에서 그대로 가져오므로,
 * 새 테마나 레이아웃을 등록하면 여기에도 자동으로 나타난다.
 */
@Composable
fun WidgetSettingsScreen(onChanged: () -> Unit) {
    val context = LocalContext.current

    var themeId by remember { mutableStateOf(WidgetThemes.byId(context, AppSettings.widgetTheme(context)).id) }
    var themes by remember { mutableStateOf(WidgetThemes.all(context)) }
    var editingPalette by remember { mutableStateOf<CustomPalette?>(null) }
    var layoutIds by remember {
        mutableStateOf(
            WidgetSlot.entries.associate { slot ->
                slot to WidgetLayouts.byId(AppSettings.widgetLayout(context, slot.key), slot.defaultLayout).id
            }
        )
    }

    editingPalette?.let { palette ->
        val exists = themes.any { it.id == palette.id }
        PaletteEditorDialog(
            initial = palette,
            onDismiss = {
                editingPalette = null
                onChanged()
            },
            onSave = {
                CustomPalettes.save(context, it)
                themes = WidgetThemes.all(context)
                themeId = it.id
                AppSettings.setWidgetTheme(context, it.id)
                editingPalette = null
                onChanged()
            },
            onDelete = if (exists) {
                {
                    CustomPalettes.delete(context, palette.id)
                    themes = WidgetThemes.all(context)
                    if (themeId == palette.id) {
                        themeId = WidgetThemes.default.id
                        AppSettings.setWidgetTheme(context, themeId)
                    }
                    editingPalette = null
                    onChanged()
                }
            } else {
                null
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
                SectionTitle("테마")
                // 실제 위젯과 같은 색으로 그린 미리보기. 가로로 넘겨 고른다.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    themes.forEach { theme ->
                        WidgetThemePreview(
                            theme = theme,
                            selected = theme.id == themeId,
                            onClick = {
                                themeId = theme.id
                                AppSettings.setWidgetTheme(context, theme.id)
                                onChanged()
                            },
                            // 사용자 팔레트는 눌러서 바로 고칠 수 있다.
                            onEdit = (theme as? CustomWidgetTheme)?.let { custom ->
                                { editingPalette = custom.source }
                            },
                        )
                    }
                    AddPaletteCard {
                        editingPalette = CustomPalette(
                            id = CustomPalettes.newId(),
                            name = "내 팔레트",
                            translucent = false,
                            colors = CustomPalettes.blank(),
                        )
                    }
                }

                WidgetSlot.entries.forEach { slot ->
                    SectionTitle("${slot.label} 레이아웃")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetLayouts.all.forEach { layout ->
                            FilterChip(
                                selected = layout.id == layoutIds[slot],
                                onClick = {
                                    layoutIds = layoutIds + (slot to layout.id)
                                    AppSettings.setWidgetLayout(context, slot.key, layout.id)
                                    onChanged()
                                },
                                label = { Text(layout.name, fontSize = 13.sp) },
                            )
                        }
                    }
                    WidgetLayouts.all.firstOrNull { it.id == layoutIds[slot] }?.let {
                        Text(
                            it.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    "테마는 색만 바꿉니다 — 어떤 테마든 모든 레이아웃에 그대로 적용됩니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
    }
}

/** 새 팔레트를 만드는 자리. */
@Composable
private fun AddPaletteCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("＋", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        Text("새 팔레트", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
