package com.yftech.comsigan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 튜토리얼이 가리킬 화면 요소들의 위치. 화면 쪽에서 [tutorialAnchor]로 등록한다. */
typealias TutorialAnchors = MutableMap<String, Rect>

val LocalTutorialAnchors = compositionLocalOf<TutorialAnchors?> { null }

@Composable
fun rememberTutorialAnchors(): TutorialAnchors = remember { mutableStateMapOf() }

/** 이 요소의 화면상 위치를 튜토리얼에 알려준다. */
fun Modifier.tutorialAnchor(key: String): Modifier = composed {
    val anchors = LocalTutorialAnchors.current
    if (anchors == null) {
        this
    } else {
        onGloballyPositioned { anchors[key] = it.boundsInRoot() }
    }
}

object TutorialTargets {
    const val SCHOOL = "school"
    const val CLASS = "class"
    const val WIDGET_TAB = "widgetTab"
}

data class TutorialStep(
    val target: String?,
    val title: String,
    val body: String,
)

private val steps = listOf(
    TutorialStep(
        target = null,
        title = "컴시간 뷰어에 오신 걸 환영해요",
        body = "학교 시간표를 앱과 홈 화면 위젯으로 봅니다.\n설정은 30초면 끝나요.",
    ),
    TutorialStep(
        target = TutorialTargets.SCHOOL,
        title = "먼저 학교를 고르세요",
        body = "학교 이름을 누르면 검색창이 열립니다.\n이름 일부만 입력해도 찾을 수 있어요.",
    ),
    TutorialStep(
        target = TutorialTargets.CLASS,
        title = "학년과 반 선택",
        body = "고른 학급이 시간표와 위젯에 함께 적용됩니다.\n언제든 눌러서 바꿀 수 있어요.",
    ),
    TutorialStep(
        target = TutorialTargets.WIDGET_TAB,
        title = "홈 화면 위젯",
        body = "위젯 탭에서 테마와 레이아웃을 고르세요.\n" +
            "위젯은 홈 화면을 길게 눌러 추가합니다.",
    ),
)

/**
 * 첫 실행 안내. 화면을 어둡게 덮고 설명할 요소만 뚫어서 보여준다.
 */
@Composable
fun TutorialOverlay(anchors: TutorialAnchors, onFinish: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val step = steps[index]
    val hole = step.target?.let { anchors[it] }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 안내 중에는 뒤쪽 화면이 눌리지 않게 막는다.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                val padding = with(density) { 8.dp.toPx() }
                val radius = with(density) { 14.dp.toPx() }
                drawContext.canvas.saveLayer(
                    Rect(Offset.Zero, size),
                    androidx.compose.ui.graphics.Paint(),
                )
                drawRect(Color.Black.copy(alpha = 0.78f))
                hole?.let {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(it.left - padding, it.top - padding),
                        size = Size(it.width + padding * 2, it.height + padding * 2),
                        cornerRadius = CornerRadius(radius, radius),
                        blendMode = BlendMode.Clear,
                    )
                }
                drawContext.canvas.restore()
            }
        }

        // 뚫린 부분을 가리지 않도록 카드를 위/아래로 배치한다.
        val alignment = when {
            hole == null -> Alignment.Center
            hole.center.y < size(density) / 2 -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }

        Column(
            modifier = Modifier
                .align(alignment)
                .padding(20.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${index + 1} / ${steps.size}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(step.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                step.body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onFinish) { Text("건너뛰기") }
                Button(onClick = {
                    if (index == steps.lastIndex) onFinish() else index++
                }) {
                    Text(if (index == steps.lastIndex) "시작하기" else "다음")
                }
            }
        }
    }
}

/** 화면 높이(px). 카드 위치를 정하는 데만 쓴다. */
@Composable
private fun size(density: androidx.compose.ui.unit.Density): Float {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return with(density) { configuration.screenHeightDp.dp.toPx() }
}
