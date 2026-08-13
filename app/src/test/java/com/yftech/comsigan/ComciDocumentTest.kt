package com.yftech.comsigan

import com.yftech.comsigan.data.ComciDocument
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 실제 컴시간 응답(한국디지털미디어고)으로 파서를 돌려 전 학급 결과를 덤프한다.
 * macOS 앱(Swift)의 같은 로직 출력과 대조하기 위한 것이다.
 */
class ComciDocumentTest {

    @Test
    fun dumpAllClasses() {
        val json = javaClass.classLoader!!.getResourceAsStream("comci-29175.json")!!
            .bufferedReader().readText()
        val document = ComciDocument(json)

        val lines = buildList {
            for (grade in document.grades) {
                for (klass in 1..document.classCount(grade)) {
                    val timetable = document.timetable(grade, klass)
                    timetable.days.forEachIndexed { dayIndex, row ->
                        row.forEachIndexed { periodIndex, cell ->
                            add(
                                "$grade|$klass|${dayIndex + 1}|${periodIndex + 1}|" +
                                    "${cell.group}${cell.subject}|${cell.teacher}|${cell.room}|" +
                                    if (cell.isChanged) "1" else "0"
                            )
                        }
                    }
                }
            }
        }

        val output = File("build/kotlin-dump.txt")
        output.parentFile?.mkdirs()
        output.writeText(lines.joinToString("\n") + "\n")

        assertTrue("덤프가 비어 있음", lines.size > 100)
    }
}
