package com.hero.ziggymusic.ext

import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.text.SimpleDateFormat
import java.util.Locale

@BindingAdapter("duration")
fun TextView.setDuration(duration: Long) {
    val simpleDateFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
    text = simpleDateFormat.format(duration)
}