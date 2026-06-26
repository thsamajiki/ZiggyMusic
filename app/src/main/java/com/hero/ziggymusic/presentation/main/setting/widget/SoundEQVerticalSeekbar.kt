package com.hero.ziggymusic.presentation.main.setting.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.withRotation

class SoundEQVerticalSeekbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.seekBarStyle,
) : AppCompatSeekBar(context, attrs, defStyleAttr) {
    var isTrackingTouch: Boolean = false
        private set

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.hero.ziggymusic.R.color.dark_gray)
        strokeWidth = resources.displayMetrics.density
    }

    private val tickCount = 13
    private val tickStartOffset = 16f * resources.displayMetrics.density
    private val tickEndOffset = 11f * resources.displayMetrics.density
    private val majorTickExtraLength = 2f * resources.displayMetrics.density
    private val trackVerticalInset = resources.getDimension(com.hero.ziggymusic.R.dimen.setting_eq_track_vertical_inset)
    private val tickTopInset = trackVerticalInset
    private val tickBottomInset = trackVerticalInset

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

    fun setTickAlpha(alpha: Float) {
        tickPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        invalidate()
    }

    override fun setProgress(progress: Int) {
        updateProgress(progress)
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

        updateProgress(newProgress)
    }

    private fun updateProgress(progress: Int) {
        super.setProgress(progress)
        syncDrawableBounds()
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