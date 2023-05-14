package com.hero.ziggymusic.ext

import android.graphics.Color
import android.view.ViewGroup
import androidx.databinding.BindingAdapter

@BindingAdapter("playStateBackground")
fun ViewGroup.setPlayStateBackground(isPlaying: Boolean) {
    // 재생 중에 따라
    if (isPlaying) {
        setBackgroundColor(Color.GRAY) // 재생 중이면 배경 색을 회색
    } else {
        setBackgroundColor(Color.TRANSPARENT)
    }
}