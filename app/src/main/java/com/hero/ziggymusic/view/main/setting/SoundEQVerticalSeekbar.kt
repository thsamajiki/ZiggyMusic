package com.hero.ziggymusic.view.main.setting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatSeekBar

class SoundEQVerticalSeekbar(
    context: Context
): AppCompatSeekBar(context) {
    var shouldChange = false
    var bandIndex: Int = 0

    constructor(context: Context, attrs: AttributeSet) : this(context)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : this(context)

    init {
        // vertical seekbar value to dB mapping example: progress 0..100 -> -12dB..+12dB
        max = 100
        progress = 50
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val gainDb = (progress - 50) * (12.0f / 50.0f)
                SoundEQSettings.setBandGain(bandIndex, gainDb)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

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

        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                progress = max - (max * event.y / height).toInt()
                onSizeChanged(width, height, 0, 0)
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
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
