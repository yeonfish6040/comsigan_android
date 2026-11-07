package com.yftech.comsigan.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Play에 실제로 배포되어 이 기기에서 받을 수 있는 업데이트를 확인한다. */
class AppUpdateChecker(context: Context) {
    private val app = context.applicationContext
    private val manager = AppUpdateManagerFactory.create(app)
    private val prefs = app.getSharedPreferences("appUpdates", Context.MODE_PRIVATE)
    private var promptedVersion: Int? = null

    var isChecking by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var hasUpdate by mutableStateOf(false)
        private set
    var showsUpdate by mutableStateOf(false)

    suspend fun check(automatically: Boolean = false) {
        if (isChecking) return
        if (automatically && System.currentTimeMillis() - prefs.getLong("lastSuccessfulCheck", 0) < 24 * 60 * 60 * 1000L) return
        isChecking = true
        message = null
        try {
            val info = suspendCancellableCoroutine { continuation ->
                manager.appUpdateInfo
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            hasUpdate = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            prefs.edit().putLong("lastSuccessfulCheck", System.currentTimeMillis()).apply()
            message = if (hasUpdate) "새 버전이 있습니다." else "최신 버전입니다."
            if (hasUpdate && (!automatically || promptedVersion != info.availableVersionCode())) {
                promptedVersion = info.availableVersionCode()
                showsUpdate = true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!automatically) {
                message = "Google Play에서 업데이트를 확인하지 못했습니다. 스토어에서 직접 확인하거나 잠시 후 다시 시도해 주세요."
            }
        } finally {
            isChecking = false
        }
    }

    fun openStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                app.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: ActivityNotFoundException) {
                message = "스토어를 열지 못했습니다. Google Play에서 컴시간을 검색해 주세요."
            }
        }
    }
}
