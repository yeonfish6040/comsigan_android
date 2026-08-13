package com.yftech.comsigan.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.data.AppSettings
import com.yftech.comsigan.widget.TimetableWidgets

/** 아래 탭 구성. 화면을 추가하려면 여기 한 줄만 늘리면 된다. */
private enum class MainTab(val label: String) {
    TIMETABLE("시간표"),
    WIDGET("위젯"),
    ABOUT("정보"),
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(MainTab.TIMETABLE) }
    val anchors = rememberTutorialAnchors()
    var showTutorial by remember { mutableStateOf(!AppSettings.isTutorialDone(context)) }

    CompositionLocalProvider(LocalTutorialAnchors provides anchors) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            // 위젯 설정에서 나올 때 한 번 더 반영한다.
                            if (tab == MainTab.WIDGET && entry != MainTab.WIDGET) {
                                TimetableWidgets.requestRefresh(context)
                            }
                            tab = entry
                        },
                        icon = {},
                        label = { Text(entry.label) },
                        modifier = if (entry == MainTab.WIDGET) {
                            Modifier.tutorialAnchor(TutorialTargets.WIDGET_TAB)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.TIMETABLE -> TimetableScreen()
                MainTab.WIDGET -> WidgetSettingsScreen(
                    onChanged = { TimetableWidgets.requestRefresh(context) },
                )
                MainTab.ABOUT -> AboutScreen()
            }
        }
    }

    if (showTutorial) {
        TutorialOverlay(
            anchors = anchors,
            onFinish = {
                AppSettings.setTutorialDone(context)
                showTutorial = false
            },
        )
    }
    }
}
