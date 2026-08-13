package com.yftech.comsigan.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * 위젯을 30초마다 다시 그리기 위한 알람. AppWidget의 updatePeriodMillis는 최소 30분이라
 * 그보다 짧은 주기는 알람을 직접 재예약하는 수밖에 없다.
 *
 * 틱마다 하는 일은 캐시로 다시 그리기뿐이고, 네트워크는 ComciService의 30분 캐시가 막는다.
 */
object WidgetRefreshScheduler {
    const val ACTION_TICK = "com.yftech.comsigan.WIDGET_TICK"
    private const val INTERVAL_MS = 30_000L
    private const val REQUEST_CODE = 1001

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        val intent = pendingIntent(context)

        // 정확 알람 권한이 있으면 그걸 쓰고, 없으면 부정확 알람으로 물러난다.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (canBeExact) {
            manager.setExact(AlarmManager.ELAPSED_REALTIME, triggerAt, intent)
        } else {
            manager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, intent)
        }
    }

    /** 세 종류 중 홈 화면에 남아 있는 위젯이 하나라도 있는지. */
    fun hasAnyWidget(context: Context): Boolean {
        val manager = android.appwidget.AppWidgetManager.getInstance(context) ?: return false
        val providers = listOf(
            NowNextWidgetReceiver::class.java,
            TodayWidgetReceiver::class.java,
            WeekWidgetReceiver::class.java,
        )
        return providers.any { provider ->
            manager.getAppWidgetIds(android.content.ComponentName(context, provider)).isNotEmpty()
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NowNextWidgetReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
