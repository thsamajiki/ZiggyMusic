package com.hero.ziggymusic.view.main.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.min

class NowPlayingVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val barBounds = RectF()
    private val currentBarHeights = FloatArray(BAR_COUNT) { PAUSED_HEIGHT_RATIO }

    private var isVisualizerPlaying = false
    private var loopAnimator: ValueAnimator? = null
    private var settleAnimator: ValueAnimator? = null

    fun setPlaying(isPlaying: Boolean) {
        if (isVisualizerPlaying == isPlaying) return

        isVisualizerPlaying = isPlaying
        if (isPlaying) {
            startLoopAnimation()
        } else {
            stopLoopAnimation()
            animateToPausedHeights()
        }
    }

    fun setBarColor(color: Int) {
        if (barPaint.color == color) return

        barPaint.color = color
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isVisualizerPlaying) {
            startLoopAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopLoopAnimation()
        settleAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val availableSize = min(width, height).toFloat()
        if (availableSize <= 0f) return

        val barWidth = availableSize * BAR_WIDTH_RATIO
        val barGap = availableSize * BAR_GAP_RATIO
        val maxBarHeight = availableSize * MAX_HEIGHT_RATIO
        val minBarHeight = availableSize * MIN_HEIGHT_RATIO
        val totalWidth = (barWidth * BAR_COUNT) + (barGap * (BAR_COUNT - 1))
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val cornerRadius = barWidth / 2f

        currentBarHeights.forEachIndexed { index, heightRatio ->
            val barHeight = (heightRatio * maxBarHeight).coerceAtLeast(minBarHeight)
            val left = startX + index * (barWidth + barGap)
            val top = centerY - barHeight / 2f
            val right = left + barWidth
            val bottom = centerY + barHeight / 2f

            barBounds.set(left, top, right, bottom)
            canvas.drawRoundRect(barBounds, cornerRadius, cornerRadius, barPaint)
        }
    }

    private fun startLoopAnimation() {
        settleAnimator?.cancel()
        if (loopAnimator?.isStarted == true) return

        loopAnimator = ValueAnimator.ofFloat(0f, PLAYING_HEIGHT_PATTERNS.size.toFloat()).apply {
            duration = LOOP_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                updatePlayingHeights(animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun stopLoopAnimation() {
        loopAnimator?.cancel()
        loopAnimator = null
    }

    private fun updatePlayingHeights(animatedStep: Float) {
        val patternIndex = animatedStep.toInt() % PLAYING_HEIGHT_PATTERNS.size
        val nextPatternIndex = (patternIndex + 1) % PLAYING_HEIGHT_PATTERNS.size
        val progress = animatedStep - animatedStep.toInt()
        val currentPattern = PLAYING_HEIGHT_PATTERNS[patternIndex]
        val nextPattern = PLAYING_HEIGHT_PATTERNS[nextPatternIndex]

        for (index in 0 until BAR_COUNT) {
            currentBarHeights[index] = lerp(currentPattern[index], nextPattern[index], progress)
        }
        invalidate()
    }

    private fun animateToPausedHeights() {
        val startHeights = currentBarHeights.copyOf()

        settleAnimator?.cancel()
        settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PAUSE_SETTLE_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                for (index in 0 until BAR_COUNT) {
                    currentBarHeights[index] = lerp(startHeights[index], PAUSED_HEIGHT_RATIO, progress)
                }
                invalidate()
            }
            start()
        }
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float {
        return start + (end - start) * progress
    }

    private companion object {
        const val BAR_COUNT = 6
        const val BAR_WIDTH_RATIO = 0.07f
        const val BAR_GAP_RATIO = 0.075f
        const val MIN_HEIGHT_RATIO = 0.12f
        const val MAX_HEIGHT_RATIO = 0.68f
        const val PAUSED_HEIGHT_RATIO = 0.22f
        const val LOOP_DURATION_MS = 1_250L
        const val PAUSE_SETTLE_DURATION_MS = 160L

        val PLAYING_HEIGHT_PATTERNS = arrayOf(
            floatArrayOf(0.34f, 0.58f, 0.76f, 0.66f, 0.46f, 0.30f),
            floatArrayOf(0.24f, 0.42f, 0.68f, 0.82f, 0.58f, 0.38f),
            floatArrayOf(0.42f, 0.70f, 0.52f, 0.34f, 0.76f, 0.62f),
            floatArrayOf(0.28f, 0.52f, 0.84f, 0.58f, 0.40f, 0.70f),
            floatArrayOf(0.56f, 0.36f, 0.62f, 0.78f, 0.64f, 0.32f),
            floatArrayOf(0.30f, 0.66f, 0.48f, 0.38f, 0.72f, 0.54f)
        )
    }
}
