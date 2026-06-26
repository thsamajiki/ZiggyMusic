package com.hero.ziggymusic.presentation.main.musictracks.widget

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

    // 검색창 영역에서 발생하는 터치를 관찰
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        onSearchTouchEvent?.invoke(event)
        return super.dispatchTouchEvent(event)
    }

    // 빈 영역 터치 소비 및 클릭 처리
    override fun onTouchEvent(event: MotionEvent): Boolean {
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