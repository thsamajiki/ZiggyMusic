package com.hero.ziggymusic.presentation.main.player.manager

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.hero.ziggymusic.playback.queue.PlaybackQueueSource
import com.hero.ziggymusic.presentation.main.player.PlayerFragment

class PlayerController(
    lifecycleOwner: LifecycleOwner,
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

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun startPlayer(initialTrackId: String = "") {
        if (playerFragment == null) {
            fragmentManager.commit {
                add(
                    fragmentContainer.id,
                    PlayerFragment.newInstance(initialTrackId),
                    PlayerFragment.TAG
                )
            }
        }

        if (!isBottomSheetCallbackAdded) {
            bottomSheetBehavior.addBottomSheetCallback(callback)
            isBottomSheetCallbackAdded = true
        }
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
