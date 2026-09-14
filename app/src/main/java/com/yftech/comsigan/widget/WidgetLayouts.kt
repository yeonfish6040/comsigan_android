package com.yftech.comsigan.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.yftech.comsigan.data.Timetable
import com.yftech.comsigan.data.TimetableCell
import com.yftech.comsigan.data.UpcomingClass

/**
 * 위젯 레이아웃. 새 레이아웃을 만들려면 이걸 구현하고 [WidgetLayouts.all]에 한 줄 추가하면 된다.
 *
 * 위젯 본체는 데이터([WidgetRenderScope])만 넘길 뿐이고, 배경·머리글·배치까지 화면에 나가는
 * 모든 것을 레이아웃이 직접 구성한다. 흔한 형태가 필요하면 [WidgetSurface]/[WidgetHeader]를
 * 쓰면 되고, 원하면 무시하고 처음부터 그려도 된다.
 *
 * 색은 반드시 [WidgetRenderScope.palette]에서 가져올 것 — 그래야 모든 테마가 모든 레이아웃에
 * 그대로 적용된다(레이아웃과 테마는 서로를 모른다).
 */
interface WidgetLayout {
    val id: String
    val name: String
    val description: String

    @Composable
    fun Content(scope: WidgetRenderScope)
}

/** 레이아웃 목록. 여기에 추가하면 앱 설정에서 바로 고를 수 있다. */
object WidgetLayouts {
    val all: List<WidgetLayout> = listOf(
        NowNextLayout,
        TodayRowLayout,
        AgendaLayout,
        WeekGridLayout,
        FocusLayout,
    )

    fun byId(id: String?, fallback: WidgetLayout): WidgetLayout =
        all.firstOrNull { it.id == id } ?: fallback
}

// MARK: 지금 수업 + 다음 수업

object NowNextLayout : WidgetLayout {
    override val id = "now"
    override val name = "지금 수업"
    override val description = "지금(또는 다음) 수업을 크게, 이어지는 수업을 아래에"

    @Composable
    override fun Content(scope: WidgetRenderScope) {
        WidgetSurface(scope) { timetable ->
            val palette = scope.palette
            val day = scope.today
            val segment = scope.segment
            val focus = scope.focusPeriod
            val cell = if (day != null && focus != null) timetable.cell(day, focus) else null

            // 오늘 수업이 끝났거나 주말이면 다음 등교일(주가 넘어가면 다음 주) 수업을 이어서 보여준다.
            val room = ((scope.size.height - 104.dp) / 30.dp).toInt().coerceIn(1, 3)
            val upcoming = scope.upcoming(room)

            Column(modifier = GlanceModifier.fillMaxSize()) {
                if (cell != null && focus != null && segment.headline != null) {
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            segment.headline!!,
                            style = TextStyle(
                                fontSize = scope.sp(10f),
                                fontWeight = FontWeight.Medium,
                                color = palette.accent,
                            ),
                        )
                        // 쉬는시간·점심에는 큰 글씨가 '다음' 수업이라는 걸 알려 준다.
                        val trailing = segment.nextLabel ?: timetable.periodTime(focus)?.label
                        if (trailing != null) {
                            Text(
                                "  $trailing",
                                style = TextStyle(fontSize = scope.sp(10f), color = palette.textMuted),
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        if (cell.isEmpty) "수업 없음" else cell.displaySubject,
                        maxLines = 1,
                        style = TextStyle(
                            fontSize = scope.sp(24f),
                            fontWeight = FontWeight.Bold,
                            color = if (cell.isChanged) palette.changed else palette.text,
                        ),
                    )
                    if (cell.teacher.isNotEmpty()) {
                        Text(
                            cell.teacher,
                            maxLines = 1,
                            style = TextStyle(fontSize = scope.sp(11f), color = palette.textMuted),
                        )
                    }
                } else {
                    Text(
                        "오늘 수업 끝",
                        style = TextStyle(
                            fontSize = scope.sp(16f),
                            fontWeight = FontWeight.Bold,
                            color = palette.text,
                        ),
                    )
                }

                Spacer(GlanceModifier.defaultWeight())

                if (upcoming.isNotEmpty()) {
                    Text("다음", style = TextStyle(fontSize = scope.sp(9f), color = palette.textMuted))
                    Spacer(GlanceModifier.height(5.dp))
                    upcoming.forEach { UpcomingRow(scope, it) }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(scope: WidgetRenderScope, item: UpcomingClass) {
    val palette = scope.palette
    Box(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 5.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .background(palette.cell)
                .cornerRadius(10.dp)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(item.label, style = TextStyle(fontSize = scope.sp(9f), color = palette.textMuted))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                item.cell.displaySubject,
                maxLines = 1,
                style = TextStyle(
                    fontSize = scope.sp(12f),
                    fontWeight = FontWeight.Medium,
                    color = if (item.cell.isChanged) palette.changed else palette.text,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                item.cell.teacher,
                maxLines = 1,
                style = TextStyle(fontSize = scope.sp(9f), color = palette.textMuted),
            )
        }
    }
}

// MARK: 오늘 교시를 가로로

object TodayRowLayout : WidgetLayout {
    override val id = "today"
    override val name = "오늘 (가로)"
    override val description = "오늘 하루 교시를 나란히"

    @Composable
    override fun Content(scope: WidgetRenderScope) {
        WidgetSurface(scope) { timetable ->
            val palette = scope.palette
            val day = scope.today
            val count = day?.let { timetable.periodCount(it) } ?: 0
            if (day == null || count <= 0) {
                WidgetMessage(scope, if (day == null) "오늘은 수업이 없습니다" else "오늘 수업 없음")
                return@WidgetSurface
            }

            val current = scope.currentPeriod
            // 칸이 좁아지면 교사명을 뺀다.
            val showsTeacher = scope.size.width / count > 46.dp

            Row(modifier = GlanceModifier.fillMaxSize()) {
                for (period in 1..count) {
                    val cell = timetable.cell(day, period)
                    val isNow = period == current
                    // 바깥 Box가 간격을 만들고, 안쪽에 배경을 줘야 칸이 서로 붙지 않는다.
                    Box(
                        GlanceModifier.defaultWeight().fillMaxHeight()
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                    ) {
                        Column(
                            modifier = GlanceModifier.fillMaxSize()
                                .background(if (isNow) palette.cellCurrent else palette.cell)
                                .cornerRadius(12.dp)
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            Text(
                                "$period",
                                style = TextStyle(
                                    fontSize = scope.sp(9f),
                                    color = if (isNow) palette.textOnCurrent else palette.textMuted,
                                ),
                            )
                            Text(
                                if (cell.isEmpty) "—" else cell.displaySubject,
                                maxLines = 1,
                                style = TextStyle(
                                    fontSize = scope.sp(13f),
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        cell.isChanged -> palette.changed
                                        isNow -> palette.textOnCurrent
                                        else -> palette.text
                                    },
                                ),
                            )
                            if (showsTeacher && cell.teacher.isNotEmpty()) {
                                Text(
                                    cell.teacher,
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = scope.sp(9f),
                                        color = if (isNow) palette.textOnCurrent else palette.textMuted,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: 오늘 교시를 세로 목록으로

object AgendaLayout : WidgetLayout {
    override val id = "agenda"
    override val name = "오늘 (목록)"
    override val description = "오늘 교시를 시각과 함께 세로로"

    @Composable
    override fun Content(scope: WidgetRenderScope) {
        WidgetSurface(scope) { timetable ->
            val palette = scope.palette
            val day = scope.today
            val count = day?.let { timetable.periodCount(it) } ?: 0
            if (day == null || count <= 0) {
                WidgetMessage(scope, if (day == null) "오늘은 수업이 없습니다" else "오늘 수업 없음")
                return@WidgetSurface
            }

            val current = scope.currentPeriod
            Column(modifier = GlanceModifier.fillMaxSize()) {
                for (period in 1..count) {
                    val cell = timetable.cell(day, period)
                    val isNow = period == current
                    Box(GlanceModifier.fillMaxWidth().defaultWeight().padding(vertical = 2.dp)) {
                        Row(
                            modifier = GlanceModifier.fillMaxSize()
                                .background(if (isNow) palette.cellCurrent else palette.cell)
                                .cornerRadius(10.dp)
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.Vertical.CenterVertically,
                        ) {
                            Text(
                                "$period",
                                style = TextStyle(
                                    fontSize = scope.sp(10f),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNow) palette.textOnCurrent else palette.textMuted,
                                ),
                                modifier = GlanceModifier.width(16.dp),
                            )
                            timetable.periodTime(period)?.let {
                                Text(
                                    it.label,
                                    style = TextStyle(
                                        fontSize = scope.sp(9f),
                                        color = if (isNow) palette.textOnCurrent else palette.textMuted,
                                    ),
                                )
                            }
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                if (cell.isEmpty) "—" else cell.displaySubject,
                                maxLines = 1,
                                style = TextStyle(
                                    fontSize = scope.sp(13f),
                                    fontWeight = FontWeight.Medium,
                                    color = when {
                                        cell.isChanged -> palette.changed
                                        isNow -> palette.textOnCurrent
                                        else -> palette.text
                                    },
                                ),
                            )
                            Spacer(GlanceModifier.defaultWeight())
                            Text(
                                cell.teacher,
                                maxLines = 1,
                                style = TextStyle(
                                    fontSize = scope.sp(9f),
                                    color = if (isNow) palette.textOnCurrent else palette.textMuted,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: 주간 표

object WeekGridLayout : WidgetLayout {
    override val id = "week"
    override val name = "주간 표"
    override val description = "월~금 전체 시간표"

    @Composable
    override fun Content(scope: WidgetRenderScope) {
        WidgetSurface(scope) { timetable ->
            val palette = scope.palette
            val today = scope.today
            val current = scope.currentPeriod
            val gutter = if (scope.size.width > 320.dp) 18.dp else 14.dp
            val showsTeacher = scope.rowHeight(timetable.maxPeriod) > 30.dp

            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 5.dp)) {
                    Spacer(GlanceModifier.width(gutter))
                    Timetable.DAY_NAMES.forEachIndexed { index, name ->
                        Text(
                            name,
                            style = TextStyle(
                                fontSize = scope.sp(11f),
                                fontWeight = FontWeight.Bold,
                                color = if (index == today) palette.accent else palette.textMuted,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }

                for (period in 1..timetable.maxPeriod) {
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        Text(
                            "$period",
                            style = TextStyle(
                                fontSize = scope.sp(9f),
                                color = palette.textMuted,
                                textAlign = TextAlign.Center,
                            ),
                            modifier = GlanceModifier.width(gutter),
                        )
                        Timetable.DAY_NAMES.indices.forEach { day ->
                            val cell = timetable.cell(day, period)
                            val isNow = day == today && period == current
                            Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(2.5.dp)) {
                                Column(
                                    modifier = GlanceModifier.fillMaxSize()
                                        .background(
                                            when {
                                                isNow -> palette.cellCurrent
                                                day == today -> palette.cellToday
                                                else -> palette.cell
                                            }
                                        )
                                        .cornerRadius(8.dp),
                                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                                    verticalAlignment = Alignment.Vertical.CenterVertically,
                                ) {
                                    Text(
                                        if (cell.isEmpty) "" else cell.displaySubject,
                                        maxLines = 1,
                                        style = TextStyle(
                                            fontSize = scope.sp(11f),
                                            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                cell.isChanged -> palette.changed
                                                isNow -> palette.textOnCurrent
                                                else -> palette.text
                                            },
                                        ),
                                    )
                                    // 줄 높이가 넉넉하면 교사명까지 채운다.
                                    if (showsTeacher && cell.teacher.isNotEmpty()) {
                                        Text(
                                            cell.teacher,
                                            maxLines = 1,
                                            style = TextStyle(
                                                fontSize = scope.sp(8f),
                                                color = if (isNow) palette.textOnCurrent else palette.textMuted,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: 과목만 크게 (머리글도 배경 상자도 없이 직접 그리는 예)

object FocusLayout : WidgetLayout {
    override val id = "focus"
    override val name = "포커스"
    override val description = "머리글 없이 지금 수업만 큼직하게"

    @Composable
    override fun Content(scope: WidgetRenderScope) {
        val palette = scope.palette
        val timetable = scope.timetable
        val day = scope.today
        val segment = scope.segment
        val focus = scope.focusPeriod
        val cell = if (timetable != null && day != null && focus != null) timetable.cell(day, focus) else null

        // WidgetSurface를 쓰지 않고 배경부터 직접 구성한다.
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(palette.background)
                .padding(14.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            val headline = segment.headline
            if (cell != null && headline != null) {
                // 쉬는시간·점심에는 "지금 쉬는시간 · 다음 5교시"처럼 상태와 다음 교시를 함께 적는다.
                Subject(
                    scope = scope,
                    caption = listOfNotNull(headline, segment.nextLabel).joinToString(" · "),
                    cell = cell,
                )
                return@Column
            }

            // 일과가 끝났으면 다음 등교일(주가 넘어가면 다음 주 월요일)을 대신 보여준다.
            val ahead = scope.upcoming(1).firstOrNull()
            if (ahead != null) {
                Subject(scope = scope, caption = ahead.label, cell = ahead.cell)
            } else {
                Text(
                    scope.status ?: "수업 없음",
                    style = TextStyle(fontSize = scope.sp(16f), color = palette.textMuted),
                )
            }
        }
    }

    @Composable
    private fun Subject(scope: WidgetRenderScope, caption: String, cell: TimetableCell) {
        val palette = scope.palette
        Text(
            caption,
            maxLines = 1,
            style = TextStyle(fontSize = scope.sp(11f), color = palette.accent),
        )
        Text(
            cell.displaySubject.ifEmpty { "공강" },
            maxLines = 1,
            style = TextStyle(
                fontSize = scope.sp(34f),
                fontWeight = FontWeight.Bold,
                color = if (cell.isChanged) palette.changed else palette.text,
            ),
        )
        if (cell.teacher.isNotEmpty()) {
            Text(
                cell.teacher,
                style = TextStyle(fontSize = scope.sp(13f), color = palette.textMuted),
            )
        }
    }
}
