package com.hero.ziggymusic.view.main.setting

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.R
import androidx.core.graphics.withRotation
import androidx.core.graphics.toColorInt

class SoundEQVerticalSeekbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.seekBarStyle,
) : AppCompatSeekBar(context, attrs, defStyleAttr) {
    var isTrackingTouch: Boolean = false
        private set

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#535353".toColorInt()
        strokeWidth = resources.displayMetrics.density
    }

    private val tickCount = 13
    private val tickStartOffset = 16f * resources.displayMetrics.density
    private val tickEndOffset = 11f * resources.displayMetrics.density
    private val majorTickExtraLength = 2f * resources.displayMetrics.density
    private val tickTopInset = 20f * resources.displayMetrics.density
    private val tickBottomInset = 20f * resources.displayMetrics.density
    private val trackVerticalInset = 20f * resources.displayMetrics.density

    init {
        secondaryProgress = 0
        setPadding(
            trackVerticalInset.toInt(),
            paddingTop,
            trackVerticalInset.toInt(),
            paddingBottom
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withRotation(-90f) {
            translate(-height.toFloat(), 0f)
            super.onDraw(this)
        }

        drawTicks(canvas)
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
        syncDrawableBounds()
        invalidate()
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        syncDrawableBounds()
        invalidate()
    }

    private fun updateProgressFromTouch(y: Float) {
        if (height <= 0 || max <= 0) return

        val trackTop = trackVerticalInset
        val trackBottom = height - trackVerticalInset
        val trackHeight = trackBottom - trackTop
        if (trackHeight <= 0f) return

        val clampedY = y.coerceIn(trackTop, trackBottom)
        val progressFromTouch = max - (max * (clampedY - trackTop) / trackHeight).toInt()
        val newProgress = progressFromTouch.coerceIn(0, max)

        super.setProgress(newProgress)

        // 세로 SeekBar에서는 progress 변경 후 내부 drawable bounds 갱신이 필요할 수 있음
        onSizeChanged(width, height, 0, 0)

        invalidate()
    }

    private fun drawTicks(canvas: Canvas) {
        if (height <= paddingTop + paddingBottom || width <= 0) return

        val top = paddingTop + tickTopInset
        val bottom = height - paddingBottom.toFloat() - tickBottomInset
        val usableHeight = bottom - top
        val centerX = width / 2f

        for (index in 0 until tickCount) {
            val ratio = index.toFloat() / (tickCount - 1).toFloat()
            val y = top + (usableHeight * ratio)
            val isMajorTick = index == 0 || index == tickCount / 2 || index == tickCount - 1
            val extraLength = if (isMajorTick) majorTickExtraLength else 0f

            canvas.drawLine(
                centerX - tickStartOffset - extraLength,
                y,
                centerX - tickEndOffset,
                y,
                tickPaint
            )
        }
    }

    private fun syncDrawableBounds() {
        if (width <= 0 || height <= 0) return

        onSizeChanged(width, height, width, height)
    }
}
