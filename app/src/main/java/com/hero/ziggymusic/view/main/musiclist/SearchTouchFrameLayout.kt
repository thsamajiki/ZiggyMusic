package com.hero.ziggymusic.view.main.musiclist

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

class SearchTouchFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    var onSearchTouchEvent: ((MotionEvent) -> Unit)? = null

    init {
        isClickable = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        onSearchTouchEvent?.invoke(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
