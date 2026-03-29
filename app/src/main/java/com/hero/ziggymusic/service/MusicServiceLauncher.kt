package com.hero.ziggymusic.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MusicServiceLauncher {
    @Volatile
    private var isStartRequested: Boolean = false

    fun startOrRefresh(context: Context) {
        dispatchAction(context, MusicService.ACTION_REFRESH_NOTIFICATION)
    }

    fun dispatchAction(
        context: Context,
        action: String,
        mediaId: String? = null
    ) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MusicService::class.java).apply {
            this.action = action
            if (!mediaId.isNullOrBlank()) {
                putExtra(MusicService.EXTRA_MEDIA_ID, mediaId)
            }
        }

        if (MusicService.isForegroundStarted || isStartRequested) {
            appContext.startService(intent)
        } else {
            isStartRequested = true
            ContextCompat.startForegroundService(appContext, intent)
        }
    }

    fun onForegroundEntered() {
        isStartRequested = false
    }

    fun onServiceDestroyed() {
        isStartRequested = false
    }
}
