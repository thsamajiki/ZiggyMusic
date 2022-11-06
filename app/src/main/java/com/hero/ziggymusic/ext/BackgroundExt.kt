package com.hero.ziggymusic.ext

import android.graphics.Color
import android.view.ViewGroup
import androidx.databinding.BindingAdapter

@BindingAdapter("playStateBackground")
fun ViewGroup.setPlayStateBackground(isPlaying: Boolean) {
    // 재생 중에 따라
    if (isPlaying) {
        // itemView 를 사용했는데 이건 리사이클러 뷰에서 뷰홀더(아이템 하나) 현재 아이템에 해당
        setBackgroundColor(Color.GRAY) // 재생중이면 배경 색을 회색
    } else {
        setBackgroundColor(Color.TRANSPARENT)
    }
}