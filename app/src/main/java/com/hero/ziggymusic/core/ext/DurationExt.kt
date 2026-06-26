package com.hero.ziggymusic.core.ext

import java.util.Locale
import java.util.concurrent.TimeUnit

fun Long?.toDurationText(): String {
    val durationMs = this?.coerceAtLeast(0L) ?: 0L
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

    return String.format(Locale.KOREA, "%02d:%02d", minutes, seconds)
}
