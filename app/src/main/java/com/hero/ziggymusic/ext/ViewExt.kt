package com.hero.ziggymusic.ext

import android.graphics.Rect
import android.view.TouchDelegate
import android.view.View
import kotlin.math.roundToInt

fun View.expandTouchArea(minTouchTargetDp: Int = 48) {
    val parentView = parent as? View ?: return
    parentView.post {
        val minTouchTargetPx = (minTouchTargetDp * resources.displayMetrics.density).roundToInt()
        val widthExpansion = (minTouchTargetPx - width).coerceAtLeast(0)
        val heightExpansion = (minTouchTargetPx - height).coerceAtLeast(0)
        val touchBounds = Rect()

        getHitRect(touchBounds)
        touchBounds.left -= widthExpansion / 2
        touchBounds.right += widthExpansion - widthExpansion / 2
        touchBounds.top -= heightExpansion / 2
        touchBounds.bottom += heightExpansion - heightExpansion / 2

        parentView.touchDelegate = TouchDelegate(touchBounds, this)
    }
}
