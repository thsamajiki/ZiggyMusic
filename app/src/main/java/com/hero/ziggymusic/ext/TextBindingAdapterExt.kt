package com.hero.ziggymusic.ext

import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.text.SimpleDateFormat

@BindingAdapter("duration")
fun TextView.setDuration(duration: Long) {
    val simpleDateFormat = SimpleDateFormat("mm:ss")
    text = simpleDateFormat.format(duration)
}