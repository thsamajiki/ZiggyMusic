package com.hero.ziggymusic.event

class Event(private var event: String, private var data: Any? = null) {
    fun getEvent(): String {
        return event
    }

    fun getData(): Any? {
        return data
    }
}