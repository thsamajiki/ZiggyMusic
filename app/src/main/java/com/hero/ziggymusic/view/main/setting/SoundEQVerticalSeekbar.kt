package com.hero.ziggymusic.view.main.setting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

class SoundEQVerticalSeekbar(
    context: Context
): AppCompatSeekBar(context) {
    var shouldChange = false

    constructor(context: Context, attrs: AttributeSet) : this(context)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : this(context)

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(height, width, oldHeight, oldWidth)
    }

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        canvas.rotate(-90.0F)
        canvas.translate(-height.toFloat(), 0F)
        super.onDraw(canvas)
    }

    fun updateThumb() {
        onSizeChanged(width, height, 0, 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) { return false }
        shouldChange = true

        when (event.getAction()) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                progress = max - (max * event.y / height).toInt()
                onSizeChanged(width, height, 0, 0)
            }
            MotionEvent.ACTION_CANCEL -> {
            }
        }

        shouldChange = true
        return true
    }

    @Synchronized
    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        shouldChange = false
    }
}