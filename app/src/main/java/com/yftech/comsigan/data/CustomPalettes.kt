package com.yftech.comsigan.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.yftech.comsigan.widget.WidgetColors
import org.json.JSONArray
import org.json.JSONObject

/** 사용자 정의 팔레트 한 개. 색은 밝기 모드와 무관하게 지정한 값 그대로 쓴다. */
data class CustomPalette(
    val id: String,
    val name: String,
    val translucent: Boolean,
    val colors: WidgetColors,
)

/**
 * 팔레트에서 색 하나를 가리키는 항목. 편집 화면은 이 목록을 그대로 훑어 만들어지므로,
 * WidgetColors에 색을 추가하면 여기 한 줄만 더하면 된다.
 */
data class ColorSlot(
    val key: String,
    val label: String,
    val get: (WidgetColors) -> Color,
    val set: (WidgetColors, Color) -> WidgetColors,
)

object CustomPalettes {
    private const val KEY = "customPalettes"

    val slots: List<ColorSlot> = listOf(
        ColorSlot("background", "배경", { it.background }, { c, v -> c.copy(background = v) }),
        ColorSlot("cell", "칸", { it.cell }, { c, v -> c.copy(cell = v) }),
        ColorSlot("cellToday", "오늘 칸", { it.cellToday }, { c, v -> c.copy(cellToday = v) }),
        ColorSlot("cellCurrent", "현재 교시 칸", { it.cellCurrent }, { c, v -> c.copy(cellCurrent = v) }),
        ColorSlot("text", "글자", { it.text }, { c, v -> c.copy(text = v) }),
        ColorSlot("textMuted", "보조 글자", { it.textMuted }, { c, v -> c.copy(textMuted = v) }),
        ColorSlot("textOnCurrent", "현재 교시 글자", { it.textOnCurrent }, { c, v -> c.copy(textOnCurrent = v) }),
        ColorSlot("accent", "강조", { it.accent }, { c, v -> c.copy(accent = v) }),
        ColorSlot("changed", "변경 수업", { it.changed }, { c, v -> c.copy(changed = v) }),
    )

    /** 새 팔레트를 만들 때 출발점이 되는 색. */
    fun blank(): WidgetColors = WidgetColors(
        background = Color(0xFF1A1620),
        cell = Color(0xFF2B2930),
        cellToday = Color(0xFF3A3648),
        cellCurrent = Color(0xFF4F378B),
        text = Color(0xFFE6E0E9),
        textMuted = Color(0xFFB0A9BA),
        textOnCurrent = Color(0xFFEADDFF),
        accent = Color(0xFFD0BCFF),
        changed = Color(0xFFFFB870),
    )

    fun load(context: Context): List<CustomPalette> {
        val raw = AppSettings.prefs(context).getString(KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val colorsJson = item.optJSONObject("colors") ?: return@mapNotNull null
            var colors = blank()
            slots.forEach { slot ->
                parseRgba(colorsJson.optString(slot.key))?.let { colors = slot.set(colors, it) }
            }
            CustomPalette(
                id = item.optString("id"),
                name = item.optString("name", "내 팔레트"),
                translucent = item.optBoolean("translucent", false),
                colors = colors,
            )
        }
    }

    fun save(context: Context, palette: CustomPalette) {
        val existing = load(context).toMutableList()
        val index = existing.indexOfFirst { it.id == palette.id }
        if (index >= 0) existing[index] = palette else existing.add(palette)
        write(context, existing)
    }

    fun delete(context: Context, id: String) {
        write(context, load(context).filterNot { it.id == id })
    }

    fun newId(): String = "custom-${System.currentTimeMillis()}"

    private fun write(context: Context, palettes: List<CustomPalette>) {
        val array = JSONArray()
        palettes.forEach { palette ->
            val colors = JSONObject()
            slots.forEach { slot -> colors.put(slot.key, formatRgba(slot.get(palette.colors))) }
            array.put(
                JSONObject()
                    .put("id", palette.id)
                    .put("name", palette.name)
                    .put("translucent", palette.translucent)
                    .put("colors", colors)
            )
        }
        AppSettings.prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    // MARK: RGBA 표기

    /** "#RRGGBBAA" / "#RRGGBB" / "#RGB"를 읽는다. 알파를 뒤에 붙이는 rgba 표기다. */
    fun parseRgba(text: String?): Color? {
        val hex = text?.trim()?.removePrefix("#") ?: return null
        val full = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("") + "FF"
            6 -> hex + "FF"
            8 -> hex
            else -> return null
        }
        val value = full.toLongOrNull(16) ?: return null
        val r = ((value shr 24) and 0xFF).toInt()
        val g = ((value shr 16) and 0xFF).toInt()
        val b = ((value shr 8) and 0xFF).toInt()
        val a = (value and 0xFF).toInt()
        return Color(red = r, green = g, blue = b, alpha = a)
    }

    /** "#RRGGBBAA"로 적는다. */
    fun formatRgba(color: Color): String {
        fun channel(value: Float) = (value * 255f).toInt().coerceIn(0, 255)
        return "#%02X%02X%02X%02X".format(
            channel(color.red), channel(color.green), channel(color.blue), channel(color.alpha)
        )
    }
}
