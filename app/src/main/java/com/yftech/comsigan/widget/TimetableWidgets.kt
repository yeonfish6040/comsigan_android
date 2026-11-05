package com.yftech.comsigan.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.LocalSize
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
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
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yftech.comsigan.data.AppSettings
import com.yftech.comsigan.data.ComciService
import com.yftech.comsigan.data.Timetable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * 갱신을 강제하기 위한 값. 설정(SharedPreferences)은 Compose가 관찰하지 못해
 * 세션이 살아 있으면 recomposition이 건너뛰어진다. 이 값을 바꿔 무효화한다.
 */
private val RevisionKey = longPreferencesKey("revision")

/** 시스템 테마가 밝기 모드를 따라가도록 하는 기반 색. */
private val SystemColors = ColorProviders(light = lightColorScheme(), dark = darkColorScheme())

/**
 * 홈 화면에 놓을 수 있는 위젯 자리. 자리마다 기본 레이아웃이 정해져 있고,
 * 앱 설정에서 다른 레이아웃으로 바꿀 수 있다.
 */
enum class WidgetSlot(val key: String, val label: String, val defaultLayout: WidgetLayout) {
    NOW("now", "작은 위젯", NowNextLayout),
    TODAY("today", "가로 위젯", TodayRowLayout),
    WEEK("week", "큰 위젯", WeekGridLayout),
}

/**
 * 시간표 위젯. 자리(slot)마다 하나씩 있고, 실제로 그릴 내용은
 * 설정에서 고른 [WidgetLayout]과 [WidgetTheme]이 정한다.
 */
abstract class BaseTimetableWidget(private val slot: WidgetSlot) : GlanceAppWidget() {

    /** 실제 크기를 그대로 받아 글자 크기와 요소 밀도를 맞춘다. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext

        // 캐시가 없거나 오래됐으면 뒤에서 받아 온 뒤 다시 그린다. 화면은 절대 기다리지 않는다.
        // 이번 주가 끝나 가면 다음 주 자료까지 채운다(금요일 방과 후에 다음 주 월요일을 보여줘야 한다).
        val schoolCode = AppSettings.school(app).code
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val fetched = ComciService.ensureWeeks(
                    app, schoolCode, AppSettings.grade(app), AppSettings.klass(app)
                )
                // 새로 받아온 게 있을 때만 다시 그린다. 늘 그리면 갱신이 끝없이 돈다.
                if (fetched) TimetableWidgets.refreshAll(app)
            }
        }

        provideContent {
            // 리비전을 읽어 두면 갱신 요청 때마다 확실히 다시 그려진다.
            currentState(RevisionKey)

            // 설정은 반드시 컴포지션 안에서 읽는다. 바깥에서 읽으면 첫 실행 값이 그대로 굳어
            // 테마·학급을 바꿔도 앱을 다시 켜기 전까지 반영되지 않는다.
            val school = AppSettings.school(app)
            val palette = WidgetThemes.byId(app, AppSettings.widgetTheme(app)).palette()
            val layout = WidgetLayouts.byId(AppSettings.widgetLayout(app, slot.key), slot.defaultLayout)
            val grade = AppSettings.grade(app)
            val klass = AppSettings.klass(app)

            val weeks = ComciService.cachedWeeks(app, school.code)
            val timetable = weeks?.current?.let {
                runCatching { it.timetable(grade, klass, app, schoolCode = school.code) }.getOrNull()
            }
            val nextWeekTimetable = weeks?.next?.let {
                runCatching { it.timetable(grade, klass, app, schoolCode = school.code) }.getOrNull()
            }
            val status = when {
                timetable != null -> null
                weeks == null -> "시간표를 받는 중…"
                else -> "${grade}학년 ${klass}반 시간표가 없습니다"
            }

            GlanceTheme(colors = SystemColors) {
                // 호스트는 데이터만 넘기고, 화면 구성은 전적으로 레이아웃이 한다.
                layout.Content(
                    WidgetRenderScope(
                        size = LocalSize.current,
                        palette = palette,
                        timetable = timetable,
                        nextWeekTimetable = nextWeekTimetable,
                        school = school,
                        grade = grade,
                        klass = klass,
                        status = status,
                    )
                )
            }
        }
    }
}

/** 자리별 위젯 — 매니페스트의 리시버와 1:1로 맞물린다. */
class NowNextWidget : BaseTimetableWidget(WidgetSlot.NOW)

class TodayWidget : BaseTimetableWidget(WidgetSlot.TODAY)

class WeekWidget : BaseTimetableWidget(WidgetSlot.WEEK)

object TimetableWidgets {
    private val receivers = listOf(
        NowNextWidgetReceiver::class.java,
        TodayWidgetReceiver::class.java,
        WeekWidgetReceiver::class.java,
    )

    /**
     * 앱에서 설정이 바뀌었을 때 위젯을 다시 그리게 한다.
     *
     * APPWIDGET_UPDATE 브로드캐스트는 이미 살아 있는 Glance 세션에서 provideGlance를 다시
     * 돌리지 않아 테마·레이아웃 변경이 반영되지 않는다. 그래서 30초 틱과 같은 경로로
     * 리시버를 깨워, 리시버가 updateAll을 부르게 한다(이 경로만 확실히 다시 그린다).
     */
    fun requestRefresh(context: Context) {
        val app = context.applicationContext
        app.sendBroadcast(
            Intent(app, NowNextWidgetReceiver::class.java).setAction(WidgetRefreshScheduler.ACTION_TICK)
        )
    }

    // 메인 스레드에서 부르면 Glance 갱신이 진행되지 않는다. 항상 백그라운드에서 돈다.
    suspend fun refreshAll(context: Context) = withContext(Dispatchers.Default) {
        // 액티비티 컨텍스트로 부르면 갱신이 전달되지 않는다. 항상 앱 컨텍스트를 쓴다.
        val app = context.applicationContext
        val manager = GlanceAppWidgetManager(app)
        val revision = System.currentTimeMillis()
        listOf(NowNextWidget(), TodayWidget(), WeekWidget()).forEach { widget ->
            runCatching {
                manager.getGlanceIds(widget.javaClass).forEach { id ->
                    updateAppWidgetState(app, id) { it[RevisionKey] = revision }
                    widget.update(app, id)
                }
            }
        }
        WidgetRefreshScheduler.schedule(app)
    }
}

/** 세 리시버가 공유하는 동작: 30초 틱 처리와 알람 예약. */
abstract class BaseTimetableWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetRefreshScheduler.ACTION_TICK) {
            // 30초 틱: 세 자리를 캐시로 다시 그리고 다음 틱을 예약한다(refreshAll이 예약한다).
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    TimetableWidgets.refreshAll(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 다른 자리의 위젯이 남아 있으면 틱을 유지한다.
        if (!WidgetRefreshScheduler.hasAnyWidget(context)) {
            WidgetRefreshScheduler.cancel(context)
        }
    }
}

class NowNextWidgetReceiver : BaseTimetableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowNextWidget()
}

class TodayWidgetReceiver : BaseTimetableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class WeekWidgetReceiver : BaseTimetableWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
