package com.hero.ziggymusic.view.main.setting

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.R

class SoundEQVerticalSeekbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.seekBarStyle,
) : AppCompatSeekBar(context, attrs, defStyleAttr) {
    var isTrackingTouch: Boolean = false
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                isTrackingTouch = true
                updateProgressFromTouch(event.y)
                performClick()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                isTrackingTouch = true
                updateProgressFromTouch(event.y)
                return true
            }

            MotionEvent.ACTION_UP -> {
                updateProgressFromTouch(event.y)
                parent?.requestDisallowInterceptTouchEvent(false)
                isTrackingTouch = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isTrackingTouch = false
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun refreshThumbState() {
        refreshDrawableState()
        invalidate()
    }

    private fun updateProgressFromTouch(y: Float) {
        if (height <= 0 || max <= 0) return

        val progressFromTouch = max - (max * y / height).toInt()
        val newProgress = progressFromTouch.coerceIn(0, max)

        super.setProgress(newProgress)

        // 세로 SeekBar에서는 progress 변경 후 내부 drawable bounds 갱신이 필요할 수 있음
        onSizeChanged(width, height, 0, 0)

        invalidate()
    }
}