package com.hero.ziggymusic.view.main.player

import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.view.doOnLayout
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
        if (isAtTargetState(state)) {
            snapToState(state)
            return
        }

        when (state) {
            State.COLLAPSED -> collapse()
            State.EXPANDED -> expand()
        }
    }

    fun updateProgress(slideOffset: Float) {
        motionLayout.setTransition(R.id.collapsedToExpanded)
        motionLayout.progress = slideOffset.coerceIn(0f, 1f)
    }

    // 애니메이션 없이 Motion 상태로 맞춘다
    fun snapToState(state: State) {
        motionLayout.setTransition(R.id.collapsedToExpanded)
        motionLayout.progress = when (state) {
            State.COLLAPSED -> COLLAPSED_PROGRESS
            State.EXPANDED -> EXPANDED_PROGRESS
        }
    }

    private fun collapse() {
        motionLayout.doOnLayout {
            motionLayout.setTransition(R.id.collapsedToExpanded)
            motionLayout.transitionToStart()
        }

        bottomSheetManager.collapse()
    }

    private fun expand() {
        motionLayout.doOnLayout {
            motionLayout.setTransition(R.id.collapsedToExpanded)
            motionLayout.transitionToEnd()
        }

        bottomSheetManager.expand()
    }

    // progress가 거의 0 또는 1이면 이미 최종 상태로 보고 같은 전환을 다시 시작하지 않는다.
    private fun isAtTargetState(state: State): Boolean =
        when (state) {
            State.COLLAPSED -> motionLayout.progress <= PROGRESS_EPSILON
            State.EXPANDED -> motionLayout.progress >= EXPANDED_PROGRESS - PROGRESS_EPSILON
        }

    private companion object {
        const val COLLAPSED_PROGRESS = 0f
        const val EXPANDED_PROGRESS = 1f
        const val PROGRESS_EPSILON = 0.01f // progress 비교용 작은 오차 허용값
    }
}
