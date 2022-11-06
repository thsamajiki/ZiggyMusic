package com.hero.ziggymusic.ext

import android.graphics.Color
import android.view.ViewGroup
import androidx.databinding.BindingAdapter

@BindingAdapter("playStateBackground")
fun ViewGroup.setPlayStateBackground(isPlaying: Boolean) {
    if (isPlaying) {
        setBackgroundColor(Color.GRAY)
    } else {
        setBackgroundColor(Color.TRANSPARENT)
    }
}