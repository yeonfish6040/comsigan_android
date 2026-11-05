package com.yftech.comsigan.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.data.AppSettings
import com.yftech.comsigan.data.ComciService
import com.yftech.comsigan.data.PeriodTime
import com.yftech.comsigan.widget.TimetableWidgets

/**
 * 일과시간(교시 시작 시각) 수정. 학교가 컴시간에 올려 둔 값이 실제 운영과
 * 다를 때 쓴다. 저장한 값은 앱과 위젯이 함께 본다.
 */
@Composable
fun ScheduleEditorDialog(onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    val context = LocalContext.current
    val school = remember { AppSettings.school(context) }

    var times by remember { mutableStateOf<List<PeriodTime>>(emptyList()) }
    /** 학교가 올려 둔 원본. "서버 값으로 되돌리기"에 쓴다. */
    var serverTimes by remember { mutableStateOf<List<PeriodTime>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    /** 자동 채우기 규칙 — 고른 교시부터 아래로 "수업 + 쉬는시간" 간격으로 다시 깐다. */
    var fillFrom by remember { mutableStateOf(1) }
    var lessonMinutes by remember { mutableStateOf(50) }
    var breakMinutes by remember { mutableStateOf(10) }

    LaunchedEffect(school.code) {
        // 캐시가 있으면 그대로 쓴다. 못 받아와도 저장해 둔 값만으로 고칠 수 있게 둔다.
        val document = runCatching { ComciService.document(context, school.code) }.getOrNull()
        serverTimes = document?.serverPeriodTimes.orEmpty()
        val resolved = AppSettings.resolvedPeriodTimes(context, school.code, serverTimes)
        times = resolved.ifEmpty { serverTimes }
        fillFrom = times.firstOrNull()?.period ?: 1
        isLoading = false
    }

    fun applyAutoFill() {
        val anchor = times.indexOfFirst { it.period == fillFrom }
        if (anchor < 0) return
        var minutes = times[anchor].hour * 60 + times[anchor].minute
        times = times.mapIndexed { index, time ->
            if (index < anchor) {
                time
            } else {
                val filled = time.copy(hour = (minutes / 60) % 24, minute = minutes % 60)
                minutes += lessonMinutes + breakMinutes
                filled
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일과시간") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${school.name}의 교시 시작 시각입니다. 학교가 올린 값이 실제와 다르면 여기서 고치세요.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isLoading) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        times.forEachIndexed { index, time ->
                            PeriodRow(
                                time = time,
                                lessonMinutes = lessonMinutes,
                                onPick = { hour, minute ->
                                    times = times.toMutableList().also {
                                        it[index] = time.copy(hour = hour, minute = minute)
                                    }
                                },
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 4.dp))

                    // "5교시부터 50분 수업 · 10분 쉬는시간" 한 번에 깔기.
                    // 점심처럼 중간에 긴 사이가 있으면 그 뒤 교시만 골라 다시 깔면 된다.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PickerChip(
                            label = "${fillFrom}교시",
                            options = times.map { it.period to "${it.period}교시" },
                            onSelect = { fillFrom = it },
                        )
                        Text("부터", fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                    StepperRow("수업", lessonMinutes, 30, 90) { lessonMinutes = it }
                    StepperRow("쉬는시간", breakMinutes, 0, 30) { breakMinutes = it }
                    TextButton(onClick = { applyAutoFill() }, enabled = times.isNotEmpty()) {
                        Text("이 간격으로 아래 교시 채우기", fontSize = 13.sp)
                    }
                }

                TextButton(
                    onClick = { times = serverTimes },
                    enabled = !isLoading && serverTimes.isNotEmpty() && times != serverTimes,
                ) {
                    Text("서버 값으로 되돌리기", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    // 서버 값으로 되돌린 경우도 명시적인 선택이다. 지우면 기본 보정값이 다시 적용된다.
                    AppSettings.setPeriodTimes(
                        context,
                        school.code,
                        times,
                    )
                    TimetableWidgets.requestRefresh(context)
                    onSaved()
                    onDismiss()
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PeriodRow(time: PeriodTime, lessonMinutes: Int, onPick: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val end = time.hour * 60 + time.minute + lessonMinutes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onPick(hour, minute) },
                    time.hour,
                    time.minute,
                    true,
                ).show()
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${time.period}교시", fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Text(time.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            "~ %02d:%02d".format((end / 60) % 24, end % 60),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun StepperRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${value}분", fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 5).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−")
        }
        TextButton(onClick = { onChange((value + 5).coerceAtMost(max)) }, enabled = value < max) {
            Text("＋")
        }
    }
}
