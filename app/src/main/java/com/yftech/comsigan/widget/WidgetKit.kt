package com.yftech.comsigan.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yftech.comsigan.data.DayPhase
import com.yftech.comsigan.data.DaySegment
import com.yftech.comsigan.data.School
import com.yftech.comsigan.data.Timetable
import com.yftech.comsigan.data.UpcomingClass
import java.util.Date

/**
 * 레이아웃에 넘어오는 것 전부. 위젯 본체는 이 값을 채워 주기만 하고,
 * 배경·머리글·배치는 전적으로 레이아웃이 정한다.
 */
class WidgetRenderScope(
    /** 이 위젯이 실제로 차지한 크기. */
    val size: DpSize,
    /** 고른 테마의 색. */
    val palette: WidgetPalette,
    /** 시간표. 아직 못 받아왔으면 null. */
    val timetable: Timetable?,
    /** 다음 주 표. 이번 주에 남은 수업이 없을 때만 쓴다. */
    val nextWeekTimetable: Timetable? = null,
    val school: School,
    val grade: Int,
    val klass: Int,
    /** 시간표가 없을 때 대신 보여줄 설명. */
    val status: String? = null,
    val now: Date = Date(),
) {
    val isNarrow = size.width < 200.dp
    val isShort = size.height < 200.dp

    /** 폭 280dp를 1.0배로 보고 글자 크기를 연속적으로 조절한다. */
    private val factor = (size.width.value / 280f).coerceIn(0.8f, 1.45f)

    fun sp(base: Float): TextUnit = (base * factor).sp

    /** 오늘 요일(월=0). 주말이면 null. */
    val today: Int? get() = Timetable.dayIndex(now)

    /** 지금이 수업인지 쉬는시간인지 점심인지. */
    val segment: DaySegment get() = timetable?.segment(now) ?: DaySegment(DayPhase.DONE)

    val currentPeriod: Int? get() = timetable?.currentPeriod(now)

    val nextPeriod: Int? get() = timetable?.nextPeriod(now)

    /** 지금(없으면 다음) 교시. 쉬는시간이면 곧 시작할 교시를 가리킨다. */
    val focusPeriod: Int? get() = currentPeriod ?: nextPeriod

    /**
     * 앞으로의 수업. 오늘 일과가 끝났으면 다음 등교일로,
     * 이번 주가 끝났으면 다음 주 월요일로 넘어간다.
     */
    fun upcoming(limit: Int): List<UpcomingClass> = Timetable.upcoming(
        current = timetable,
        next = nextWeekTimetable,
        now = now,
        afterPeriod = focusPeriod ?: Timetable.SKIP_TODAY,
        limit = limit,
    )

    /** 한 줄에 쓸 수 있는 높이. 넉넉하면 교사명까지 넣는 식으로 쓴다. */
    fun rowHeight(rows: Int): Dp = if (rows > 0) (size.height - 34.dp) / rows else 0.dp
}

/**
 * 흔한 형태(배경 + 여백 + 머리글)를 한 번에 만들어 주는 도우미.
 * 쓰고 싶지 않은 레이아웃은 무시하고 처음부터 직접 그려도 된다.
 */
@Composable
fun WidgetSurface(
    scope: WidgetRenderScope,
    showHeader: Boolean = true,
    padding: Dp = if (scope.isNarrow) 12.dp else 14.dp,
    content: @Composable (Timetable) -> Unit,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(scope.palette.background)
            .padding(padding),
    ) {
        if (showHeader) {
            WidgetHeader(scope)
            Spacer(GlanceModifier.height(9.dp))
        }
        val timetable = scope.timetable
        if (timetable == null) {
            WidgetMessage(scope, scope.status ?: "시간표를 불러오지 못했습니다")
        } else {
            content(timetable)
        }
    }
}

/** 학급·요일·학교명 머리글. */
@Composable
fun WidgetHeader(scope: WidgetRenderScope) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            "${scope.grade}-${scope.klass}",
            style = TextStyle(
                fontSize = scope.sp(12f),
                fontWeight = FontWeight.Bold,
                color = scope.palette.text,
            ),
        )
        Spacer(GlanceModifier.width(4.dp))
        scope.today?.let {
            Text(
                "${Timetable.DAY_NAMES[it]}요일",
                style = TextStyle(fontSize = scope.sp(11f), color = scope.palette.textMuted),
            )
        }
        // 좁은 위젯에서는 학교명을 접는다.
        if (!scope.isNarrow) {
            Spacer(GlanceModifier.defaultWeight())
            Text(
                scope.school.name,
                maxLines = 1,
                style = TextStyle(fontSize = scope.sp(10f), color = scope.palette.textMuted),
            )
        }
    }
}

/** 가운데 정렬된 안내 문구. */
@Composable
fun WidgetMessage(scope: WidgetRenderScope, text: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(text, style = TextStyle(fontSize = scope.sp(12f), color = scope.palette.textMuted))
    }
}
