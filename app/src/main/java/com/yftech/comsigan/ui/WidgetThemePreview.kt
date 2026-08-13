package com.yftech.comsigan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.widget.WidgetTheme

/** 투명 테마가 어떻게 보이는지 알 수 있도록 미리보기 뒤에 깔아 두는 가짜 배경화면. */
private val WallpaperBrush = Brush.linearGradient(
    listOf(Color(0xFF6D7BA6), Color(0xFF2E3350), Color(0xFF7C6A8A))
)

/**
 * 테마 한 개를 실제 위젯과 같은 색 구성으로 축소해 보여준다.
 * 색은 위젯이 쓰는 것과 동일한 [WidgetTheme.colors]에서 가져온다.
 */
@Composable
fun WidgetThemePreview(
    theme: WidgetTheme,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    val colors = theme.colors(dark = isSystemInDarkTheme())

    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                }
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                // 투명 테마는 배경화면 위에 얹어 보여준다.
                .background(if (theme.translucent) WallpaperBrush else Brush.linearGradient(listOf(colors.background, colors.background)))
                .padding(6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background)
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("3-4", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    Text(" 목", fontSize = 9.sp, color = colors.textMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MiniCell("커문", colors.cell, colors.text, Modifier.weight(1f))
                    MiniCell("DB", colors.cellToday, colors.text, Modifier.weight(1f))
                    MiniCell("공수", colors.cellCurrent, colors.textOnCurrent, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MiniCell("빅데", colors.cell, colors.text, Modifier.weight(1f))
                    MiniCell("응개", colors.cell, colors.changed, Modifier.weight(1f))
                    MiniCell("비영", colors.cell, colors.text, Modifier.weight(1f))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                theme.name,
                fontSize = 12.sp,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            onEdit?.let {
                Text(
                    "편집",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { it() },
                )
            }
        }
    }
}

@Composable
private fun MiniCell(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 8.sp, color = textColor, maxLines = 1)
    }
}
