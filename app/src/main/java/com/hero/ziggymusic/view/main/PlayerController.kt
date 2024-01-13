package com.hero.ziggymusic.view.main

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.hero.ziggymusic.view.main.player.PlayerFragment

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

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun startPlayer() {
        fragmentManager.commit {
            add(
                fragmentContainer.id,
                PlayerFragment.newInstance(""),
                PlayerFragment.TAG
            )
        }
        bottomSheetBehavior.addBottomSheetCallback(callback)
    }

    fun changeMusic(musicId: String) {
        playerFragment?.changeMusic(musicId)
    }
}