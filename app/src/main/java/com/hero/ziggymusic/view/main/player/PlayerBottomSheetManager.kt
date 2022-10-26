package com.hero.ziggymusic.view.main.player

import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior

class PlayerBottomSheetManager(
    lifecycle: Lifecycle,
    private val rootView: ViewGroup,
    private val bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback
) : DefaultLifecycleObserver {
    private val bottomSheetBehavior: BottomSheetBehavior<ViewGroup>?
        get() {
            val root = rootView.parent as? ViewGroup
            return if (root != null) {
                kotlin.runCatching { BottomSheetBehavior.from(root) }.getOrNull()
            } else null
        }

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        addCallback()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        removeCallback()
    }

    fun collapse() {
        rootView.doOnLayout {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    fun expand() {
        rootView.doOnLayout {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    fun hide() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
    }

    fun getCurrentState(): Int? {
        return bottomSheetBehavior?.state
    }

    private fun addCallback() {
        bottomSheetBehavior?.addBottomSheetCallback(bottomSheetCallback)
    }

    private fun removeCallback() {
        bottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallback)
    }
}