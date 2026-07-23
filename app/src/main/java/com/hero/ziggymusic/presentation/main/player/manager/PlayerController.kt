package com.hero.ziggymusic.presentation.main.player.manager

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.hero.ziggymusic.playback.queue.PlaybackQueueSource
import com.hero.ziggymusic.presentation.main.player.PlayerFragment

class PlayerController(
    private val lifecycleOwner: LifecycleOwner,
    private val fragmentContainer: ViewGroup,
    private val fragmentManager: FragmentManager,
    private val onStateChanged: (newState: Int) -> Unit
) : DefaultLifecycleObserver {
    private val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(fragmentContainer)

    private val playerFragment: PlayerFragment?
        get() = fragmentManager.findFragmentByTag(PlayerFragment.TAG) as? PlayerFragment

    private val callback: BottomSheetBehavior.BottomSheetCallback =
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                onStateChanged(newState)
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        }

    private var isBottomSheetCallbackAdded = false

    // 비동기 Fragment 커밋이 끝나기 전 중복 추가를 막는다.
    private var isPlayerFragmentAddPending = false

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun startPlayer(initialTrackId: String = ""): Boolean {
        val canCommitFragment =
            !fragmentManager.isDestroyed &&
                    !fragmentManager.isStateSaved &&
                    lifecycleOwner.lifecycle.currentState
                        .isAtLeast(Lifecycle.State.RESUMED)

        if (!canCommitFragment) {
            return false
        }

        if (playerFragment == null && !isPlayerFragmentAddPending) {
            isPlayerFragmentAddPending = true

            fragmentManager.commit {
                setReorderingAllowed(true)

                add(
                    fragmentContainer.id,
                    PlayerFragment.newInstance(initialTrackId),
                    PlayerFragment.TAG
                )

                runOnCommit {
                    isPlayerFragmentAddPending = false
                }
            }
        }

        if (!isBottomSheetCallbackAdded) {
            bottomSheetBehavior.addBottomSheetCallback(callback)
            isBottomSheetCallbackAdded = true
        }

        return true
    }

    fun changeMusic(
        id: String,
        queueSource: PlaybackQueueSource
    ): Boolean {
        return playerFragment?.changeMusic(
            id = id,
            queueSource = queueSource
        ) ?: false
    }
}
