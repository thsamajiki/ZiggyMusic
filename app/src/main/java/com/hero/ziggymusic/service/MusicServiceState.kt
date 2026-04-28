package com.hero.ziggymusic.service

internal object MusicServiceState {
    @Volatile
    var isForegroundStarted: Boolean = false
        private set

    @Volatile
    private var isStartRequested: Boolean = false

    @Synchronized
    fun markStartRequested(): Boolean {
        if (isForegroundStarted || isStartRequested) return false
        isStartRequested = true
        return true
    }

    @Synchronized
    fun onForegroundEntered() {
        isForegroundStarted = true
        isStartRequested = false
    }

    @Synchronized
    fun reset() {
        isForegroundStarted = false
        isStartRequested = false
    }
}
