package com.yftech.comsigan.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yftech.comsigan.data.AppSettings
import kotlinx.coroutines.launch

/** 개인정보 처리방침 페이지. */
private const val PRIVACY_POLICY_URL =
    "https://sneaky-parrot-647.notion.site/3bb874aebd068010b5f4e988af1a677f"

@Composable
fun AboutScreen(updates: AppUpdateChecker, onScheduleSaved: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val school = AppSettings.school(context)
    var showScheduleEditor by remember { mutableStateOf(false) }

    if (showScheduleEditor) {
        ScheduleEditorDialog(
            onDismiss = { showScheduleEditor = false },
            onSaved = onScheduleSaved,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("컴시간", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "버전 ${versionName(context)}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { scope.launch { updates.check() } }, enabled = !updates.isChecking) {
                Text(if (updates.isChecking) "확인 중…" else "업데이트 확인")
            }
            TextButton(onClick = { updates.openStore() }) { Text("Google Play에서 보기") }
        }
        updates.message?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        MenuRow("개인정보 처리방침") {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        MenuRow("일과시간 수정", trailing = "›", subtitle = "교시 시작 시각이 실제와 다를 때") {
            showScheduleEditor = true
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text("현재 학급", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            "${school.name} ${AppSettings.grade(context)}학년 ${AppSettings.klass(context)}반",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "시간표 자료는 컴시간알리미(comci.net)에서 가져옵니다.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MenuRow(
    title: String,
    trailing: String = "\u2197",
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(trailing, color = MaterialTheme.colorScheme.primary)
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
