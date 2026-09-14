package com.yftech.comsigan

import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 위젯 추가 화면의 미리보기(`android:previewLayout`)는 런처가 RemoteViews로 부풀린다.
 * RemoteViews는 허용 목록에 있는 위젯만 받아 주므로(android.view.View는 없다),
 * 목록에 없는 태그가 하나라도 섞이면 미리보기가 통째로 안 뜬다.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewInflationTest {

    private fun inflate(layout: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val views = RemoteViews(context.packageName, layout)
        // 런처가 하는 것과 같은 경로. 허용 목록 밖의 태그면 여기서 InflateException이 난다.
        assertNotNull(views.apply(context, null))
    }

    @Test
    fun nowPreviewInflates() = inflate(R.layout.widget_preview_now)

    @Test
    fun todayPreviewInflates() = inflate(R.layout.widget_preview_today)

    @Test
    fun weekPreviewInflates() = inflate(R.layout.widget_preview_week)
}
