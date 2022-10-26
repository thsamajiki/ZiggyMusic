package com.hero.ziggymusic.view.main.player

import androidx.constraintlayout.motion.widget.MotionLayout
import com.hero.ziggymusic.R

class PlayerMotionManager(
    private val motionLayout: MotionLayout,
    private val bottomSheetManager: PlayerBottomSheetManager
) {
    enum class State {
        COLLAPSED,
        EXPANDED
    }

    fun changeState(state: State) {
        when (state) {
            State.COLLAPSED -> collapse()
            State.EXPANDED -> expand()
        }
    }

    private fun collapse() {
        motionLayout.setTransition(R.id.expanded_to_collapsed)
        motionLayout.progress = 0f
        motionLayout.transitionToEnd()

        bottomSheetManager.collapse()
    }

    private fun expand() {
        motionLayout.setTransition(R.id.collapsed_to_expanded)
        motionLayout.progress = 0f
        motionLayout.transitionToEnd()

        bottomSheetManager.expand()
    }
}