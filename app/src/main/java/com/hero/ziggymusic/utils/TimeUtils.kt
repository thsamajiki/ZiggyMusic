package com.hero.ziggymusic.utils

import java.text.SimpleDateFormat
import java.util.*

class TimeUtils {
    // 시간 관련 함수들을 클래스로 모아둠
    private var instance: TimeUtils? = null

    companion object {
        private var timeUtils: TimeUtils? = null

        fun getInstance(): TimeUtils {
            return timeUtils ?: synchronized(this) {
                timeUtils ?: TimeUtils().also {
                    timeUtils = it
                }
            }
        }
    }

    fun convertTimeFormat(timestamp: Long, format: String?): String? {
        val dateFormat = SimpleDateFormat(format)
        val date = Date()
        date.time = timestamp
        return dateFormat.format(date)
    }

    fun convertTimeFormat(date: Date?, format: String?): String? {
        val dateFormat = SimpleDateFormat(format)
        return dateFormat.format(date)
    }
}