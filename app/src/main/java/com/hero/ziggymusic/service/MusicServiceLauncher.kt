package com.hero.ziggymusic.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Foreground Service 시작/갱신을 중앙에서 관리.
 *
 * - 서비스가 이미 실행 중이면 startService로 Intent만 전달 (불필요한 startForegroundService() 최소화)
 * - 서비스가 실행 중이 아니면 O+에서 startForegroundService로 승격
 */
object MusicServiceLauncher {
    fun startOrRefresh(context: Context) {
        dispatchAction(context, MusicService.ACTION_REFRESH_NOTIFICATION)
    }

    fun dispatchAction(context: Context, action: String) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MusicService::class.java).apply {
            this.action = action
        }

        if (MusicService.isForegroundStarted) {
            appContext.startService(intent)
        } else {
            ContextCompat.startForegroundService(appContext, intent)
        }
    }
}
