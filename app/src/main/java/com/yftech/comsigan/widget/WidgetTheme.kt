package com.yftech.comsigan.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.yftech.comsigan.data.CustomPalette
import com.yftech.comsigan.data.CustomPalettes

/**
 * 위젯이 쓰는 색 한 벌. 밝기 모드별로 한 세트씩 정의한다.
 * 여기 값만 채우면 위젯(Glance)과 앱 미리보기(Compose)가 같은 색을 쓴다.
 */
data class WidgetColors(
    val background: Color,
    /** 일반 칸 배경. */
    val cell: Color,
    /** 오늘 요일 칸 배경. */
    val cellToday: Color,
    /** 현재 교시 칸 배경. */
    val cellCurrent: Color,
    val text: Color,
    val textMuted: Color,
    /** 현재 교시 칸 위의 글자색. */
    val textOnCurrent: Color,
    val accent: Color,
    /** 변경·보강된 수업. */
    val changed: Color,
)

/** 위젯에 넘길 색. [WidgetColors]의 밝은/어두운 짝에서 만들어진다. */
data class WidgetPalette(
    val background: ColorProvider,
    val cell: ColorProvider,
    val cellToday: ColorProvider,
    val cellCurrent: ColorProvider,
    val text: ColorProvider,
    val textMuted: ColorProvider,
    val textOnCurrent: ColorProvider,
    val accent: ColorProvider,
    val changed: ColorProvider,
)

/**
 * 위젯 테마. 새 테마를 만들려면 이걸 구현하고 [WidgetThemes.all]에 한 줄 추가하면 된다.
 * [translucent]가 true면 앱 미리보기에서 배경화면이 비치는 걸 보여준다.
 */
interface WidgetTheme {
    val id: String
    val name: String
    val translucent: Boolean get() = false

    fun colors(dark: Boolean): WidgetColors
}

/** 밝은/어두운 색을 묶어 Glance가 쓰는 형태로 바꾼다. */
fun WidgetTheme.palette(): WidgetPalette {
    val light = colors(dark = false)
    val dark = colors(dark = true)
    fun pair(pick: (WidgetColors) -> Color) = ColorProvider(day = pick(light), night = pick(dark))
    return WidgetPalette(
        background = pair { it.background },
        cell = pair { it.cell },
        cellToday = pair { it.cellToday },
        cellCurrent = pair { it.cellCurrent },
        text = pair { it.text },
        textMuted = pair { it.textMuted },
        textOnCurrent = pair { it.textOnCurrent },
        accent = pair { it.accent },
        changed = pair { it.changed },
    )
}

/** 시스템 밝기를 따르는 기본 테마 — Material 기본 색을 그대로 쓴다. */
private object SystemTheme : WidgetTheme {
    override val id = "system"
    override val name = "시스템"

    override fun colors(dark: Boolean): WidgetColors {
        val scheme = if (dark) darkColorScheme() else lightColorScheme()
        return WidgetColors(
            background = scheme.surface,
            cell = scheme.surfaceVariant,
            cellToday = scheme.secondaryContainer,
            cellCurrent = scheme.primaryContainer,
            text = scheme.onSurface,
            textMuted = scheme.onSurfaceVariant,
            textOnCurrent = scheme.onPrimaryContainer,
            accent = scheme.primary,
            changed = if (dark) Color(0xFFFFB870) else Color(0xFFC2620A),
        )
    }
}

/** 배경이 비치는 테마 — 어두운 배경화면용(밝은 글씨). */
private object TransparentLightTextTheme : WidgetTheme {
    override val id = "transparent"
    override val name = "투명 · 밝은 글씨"
    override val translucent = true

    override fun colors(dark: Boolean) = WidgetColors(
        background = Color.Transparent,
        cell = Color(0x38000000),
        cellToday = Color(0x55000000),
        cellCurrent = Color(0x99FFFFFF),
        text = Color.White,
        textMuted = Color(0xCCFFFFFF),
        textOnCurrent = Color(0xFF1D1B20),
        accent = Color(0xFFD0BCFF),
        changed = Color(0xFFFFC078),
    )
}

/** 배경이 비치는 테마 — 밝은 배경화면용(어두운 글씨). */
private object TransparentDarkTextTheme : WidgetTheme {
    override val id = "transparentDark"
    override val name = "투명 · 어두운 글씨"
    override val translucent = true

    override fun colors(dark: Boolean) = WidgetColors(
        background = Color.Transparent,
        cell = Color(0x30FFFFFF),
        cellToday = Color(0x55FFFFFF),
        cellCurrent = Color(0xAA000000),
        text = Color(0xFF14121A),
        textMuted = Color(0xCC14121A),
        textOnCurrent = Color.White,
        accent = Color(0xFF4F378B),
        changed = Color(0xFF8A3D00),
    )
}

/** 밝기 모드와 무관하게 항상 어두운 테마. */
private object MidnightTheme : WidgetTheme {
    override val id = "midnight"
    override val name = "미드나이트"

    override fun colors(dark: Boolean) = WidgetColors(
        background = Color(0xF21A1620),
        cell = Color(0xFF2B2930),
        cellToday = Color(0xFF3A3648),
        cellCurrent = Color(0xFF4F378B),
        text = Color(0xFFE6E0E9),
        textMuted = Color(0xFFB0A9BA),
        textOnCurrent = Color(0xFFEADDFF),
        accent = Color(0xFFD0BCFF),
        changed = Color(0xFFFFB870),
    )
}

/** 밝기 모드와 무관하게 항상 밝은 테마. */
private object PaperTheme : WidgetTheme {
    override val id = "paper"
    override val name = "페이퍼"

    override fun colors(dark: Boolean) = WidgetColors(
        background = Color(0xFAFDF9FF),
        cell = Color(0xFFEDE8F2),
        cellToday = Color(0xFFDDE2FA),
        cellCurrent = Color(0xFFCFC4F5),
        text = Color(0xFF1D1B20),
        textMuted = Color(0xFF5F5A68),
        textOnCurrent = Color(0xFF21005D),
        accent = Color(0xFF6750A4),
        changed = Color(0xFFB35309),
    )
}

/** 바다색 계열 — 강조가 뚜렷한 대안 테마. */
private object OceanTheme : WidgetTheme {
    override val id = "ocean"
    override val name = "오션"

    override fun colors(dark: Boolean) = if (dark) {
        WidgetColors(
            background = Color(0xF2071A24),
            cell = Color(0xFF12303F),
            cellToday = Color(0xFF17415A),
            cellCurrent = Color(0xFF0B6E8F),
            text = Color(0xFFDCF3FF),
            textMuted = Color(0xFF9FC4D6),
            textOnCurrent = Color(0xFFE6F7FF),
            accent = Color(0xFF6FD6F5),
            changed = Color(0xFFFFC078),
        )
    } else {
        WidgetColors(
            background = Color(0xFAF1FAFE),
            cell = Color(0xFFDDEEF6),
            cellToday = Color(0xFFC7E4F2),
            cellCurrent = Color(0xFF0B6E8F),
            text = Color(0xFF06222E),
            textMuted = Color(0xFF4E6C79),
            textOnCurrent = Color.White,
            accent = Color(0xFF0B6E8F),
            changed = Color(0xFFB35309),
        )
    }
}

/** 사용자가 앱에서 만든 팔레트를 테마로 감싼 것. */
class CustomWidgetTheme(private val palette: CustomPalette) : WidgetTheme {
    override val id = palette.id
    override val name = palette.name
    override val translucent = palette.translucent
    val source: CustomPalette get() = palette

    override fun colors(dark: Boolean) = palette.colors
}

/** 테마 목록. 기본 테마에 사용자 팔레트가 이어 붙는다. */
object WidgetThemes {
    /** 앱에 내장된 테마. 여기에 추가하면 앱 설정에도 자동으로 뜬다. */
    val builtIn: List<WidgetTheme> = listOf(
        SystemTheme,
        TransparentLightTextTheme,
        TransparentDarkTextTheme,
        MidnightTheme,
        PaperTheme,
        OceanTheme,
    )

    val default: WidgetTheme = SystemTheme

    fun all(context: Context): List<WidgetTheme> =
        builtIn + CustomPalettes.load(context).map { CustomWidgetTheme(it) }

    fun byId(context: Context, id: String?): WidgetTheme =
        all(context).firstOrNull { it.id == id } ?: default
}
