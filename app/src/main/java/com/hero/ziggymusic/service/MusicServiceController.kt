package com.hero.ziggymusic.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MusicServiceController {
    fun refreshIfRunning(
        context: Context,
        mediaId: String? = null
    ) {
        if (!MusicServiceState.isForegroundStarted) return

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

        if (MusicServiceState.isForegroundStarted) {
            appContext.startService(intent)
        } else if (startIfNeeded && MusicServiceState.markStartRequested()) {
            ContextCompat.startForegroundService(appContext, intent)
        }
    }
}
