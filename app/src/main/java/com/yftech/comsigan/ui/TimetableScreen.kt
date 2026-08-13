package com.yftech.comsigan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.data.AppSettings
import com.yftech.comsigan.data.ComciService
import com.yftech.comsigan.data.School
import com.yftech.comsigan.data.Timetable
import com.yftech.comsigan.data.TimetableCell
import com.yftech.comsigan.widget.TimetableWidgets
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 변경/보강된 수업 강조색. */
private val ChangedColor = Color(0xFFE8871A)
private val ChangedContainer = Color(0xFFFFE7C7)

@Composable
fun TimetableScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var school by remember { mutableStateOf(AppSettings.school(context)) }
    var grade by remember { mutableStateOf(AppSettings.grade(context)) }
    var klass by remember { mutableStateOf(AppSettings.klass(context)) }

    var timetable by remember { mutableStateOf<Timetable?>(null) }
    var classCounts by remember { mutableStateOf<List<Int>>(emptyList()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val now = remember { Date() }

    fun load(force: Boolean = false) {
        scope.launch {
            isLoading = true
            AppSettings.setClass(context, grade, klass)
            AppSettings.setSchool(context, school)
            TimetableWidgets.requestRefresh(context)
            try {
                val document = ComciService.document(context, school.code, force)
                classCounts = document.classCounts
                timetable = document.timetable(grade, klass)
                errorText = null
                TimetableWidgets.requestRefresh(context)
            } catch (error: Exception) {
                errorText = error.message ?: "시간표를 불러오지 못했습니다."
            }
            isLoading = false
        }
    }

    LaunchedEffect(school.code, grade, klass) { load() }

    if (showSearch) {
        SchoolSearchDialog(
            onDismiss = { showSearch = false },
            onSelect = { picked ->
                showSearch = false
                school = picked
                grade = 1
                klass = 1
            },
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(
                schoolName = school.name,
                grade = grade,
                klass = klass,
                classCounts = classCounts,
                isLoading = isLoading,
                onSearchSchool = { showSearch = true },
                onSelectGrade = {
                    grade = it
                    klass = klass.coerceAtMost((classCounts.getOrNull(it) ?: klass).coerceAtLeast(1))
                },
                onSelectClass = { klass = it },
                onRefresh = { load(force = true) },
            )

            errorText?.let {
                Surface(
                    color = ChangedContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(10.dp),
                        color = ChangedColor,
                        fontSize = 13.sp,
                    )
                }
            }

            val current = timetable
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    current != null -> WeekGrid(current, now)
                    isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> Text("시간표가 없습니다.", Modifier.align(Alignment.Center))
                }
            }

            current?.let { Footer(it) }
        }
    }
}

@Composable
private fun Header(
    schoolName: String,
    grade: Int,
    klass: Int,
    classCounts: List<Int>,
    isLoading: Boolean,
    onSearchSchool: () -> Unit,
    onSelectGrade: (Int) -> Unit,
    onSelectClass: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val grades = remember(classCounts) {
        if (classCounts.size <= 1) listOf(1, 2, 3)
        else (1 until classCounts.size).filter { classCounts[it] > 0 }
    }
    val classes = remember(classCounts, grade) {
        val count = (classCounts.getOrNull(grade) ?: 10).coerceAtLeast(1)
        (1..count).toList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSearchSchool() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = schoolName.ifEmpty { "학교 선택" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(" ▾", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            }
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text(if (isLoading) "불러오는 중" else "↻ 새로고침", fontSize = 13.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PickerChip("${grade}학년", grades.map { it to "${it}학년" }, onSelectGrade)
            PickerChip("${klass}반", classes.map { it to "${it}반" }, onSelectClass)
        }
    }
}

@Composable
private fun PickerChip(label: String, options: List<Pair<Int, String>>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("$label ▾", fontSize = 13.sp) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(timetable: Timetable, now: Date) {
    val today = Timetable.dayIndex(now)
    val currentPeriod = timetable.currentPeriod(now)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // 요일 머리글 — 오늘 요일만 알약 배경으로 강조한다.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.width(34.dp))
            Timetable.DAY_NAMES.forEachIndexed { index, name ->
                val isToday = index == today
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isToday) Modifier.background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp),
                            ) else Modifier
                        )
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 남는 세로 공간을 교시 수만큼 나눠 화면을 꽉 채운다.
        for (period in 1..timetable.maxPeriod) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Column(
                    modifier = Modifier.width(34.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "$period",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (period == currentPeriod) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    timetable.periodTime(period)?.let {
                        Text(
                            it.label,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Timetable.DAY_NAMES.indices.forEach { day ->
                    CellView(
                        cell = timetable.cell(day, period),
                        isToday = day == today,
                        isNow = day == today && period == currentPeriod,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CellView(cell: TimetableCell, isToday: Boolean, isNow: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        cell.isEmpty -> scheme.surfaceVariant.copy(alpha = 0.25f)
        cell.isChanged -> ChangedContainer
        isNow -> scheme.primaryContainer
        isToday -> scheme.secondaryContainer.copy(alpha = 0.45f)
        else -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .background(background, shape)
            .then(if (isNow) Modifier.border(2.dp, scheme.primary, shape) else Modifier)
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (cell.isEmpty) {
            Text("·", color = scheme.outlineVariant, fontSize = 14.sp)
        } else {
            Text(
                cell.displaySubject,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = if (cell.isChanged) ChangedColor else scheme.onSurface,
            )
            if (cell.teacher.isNotEmpty()) {
                Text(
                    cell.teacher,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (cell.room.isNotEmpty()) {
                Text(cell.room, fontSize = 9.sp, maxLines = 1, color = scheme.outline)
            }
        }
    }
}

@Composable
private fun Footer(timetable: Timetable) {
    val time = remember(timetable.fetchedAt) {
        SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(timetable.fetchedAt))
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "갱신 ${timetable.sourceUpdatedAt.take(16)} · 조회 $time",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).background(ChangedContainer, CircleShape))
            Text("변경된 수업", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
