package com.yftech.comsigan

import com.yftech.comsigan.data.ComciException
import com.yftech.comsigan.data.Http
import java.io.IOException
import java.net.ServerSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ComciConnectionTest {
    @Test
    fun closedConnectionHasActionableMessageAndPreservesOriginalCause() {
        // 외부 서버 상태와 무관하게 응답 없이 연결을 닫는 현재 증상을 재현한다.
        ServerSocket(0).use { server ->
            val responder = Thread {
                try {
                    // HttpURLConnection은 응답 머리글을 못 받으면 자체 재시도를 할 수 있다.
                    repeat(2) {
                        server.accept().use { connection ->
                            val reader = connection.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { /* 요청 머리글만 읽는다. */ }
                        }
                    }
                } catch (_: IOException) {
                    // 테스트 종료 후 서버 소켓이 닫힌 경우.
                }
            }.apply { isDaemon = true; start() }
            val error = assertThrows(ComciException::class.java) {
                Http.getBytes("http://127.0.0.1:${server.localPort}/st")
            }
            assertEquals("시간표 서버가 응답 전에 연결을 종료했습니다. 잠시 후 다시 시도해 주세요.", error.message)
            assertTrue(error.cause is IOException)
            responder.join(1_000)
        }
    }
}
