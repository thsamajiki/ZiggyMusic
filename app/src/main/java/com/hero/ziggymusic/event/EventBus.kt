package com.hero.ziggymusic.event

import com.squareup.otto.Bus
import com.squareup.otto.ThreadEnforcer

class EventBus: Bus(ThreadEnforcer.ANY) {
    companion object {
        private var bus: EventBus? = null

        fun getInstance(): Bus {
            return bus ?: EventBus().apply { bus = this }
        }
    }
}