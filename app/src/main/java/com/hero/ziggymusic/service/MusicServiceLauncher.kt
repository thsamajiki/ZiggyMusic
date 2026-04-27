package com.hero.ziggymusic.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MusicServiceLauncher {
    @Volatile
    private var isStartRequested: Boolean = false

    fun refreshIfRunning(
        context: Context,
        mediaId: String? = null
    ) {
        if (!MusicService.isForegroundStarted) return

        dispatchAction(
            context = context,
            action = MusicService.ACTION_REFRESH_NOTIFICATION,
            mediaId = mediaId,
            startIfNeeded = false
        )
    }

    fun dispatchAction(
        context: Context,
        action: String,
        mediaId: String? = null,
        startIfNeeded: Boolean = true
    ) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, MusicService::class.java).apply {
            this.action = action
            if (!mediaId.isNullOrBlank()) {
                putExtra(MusicService.EXTRA_MEDIA_ID, mediaId)
            }
        }

        if (MusicService.isForegroundStarted) {
            appContext.startService(intent)
        } else if (startIfNeeded && !isStartRequested) {
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
