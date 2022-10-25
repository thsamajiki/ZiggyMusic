package com.hero.ziggymusic.view.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.hero.ziggymusic.view.main.musiclist.MusicListFragment
import com.hero.ziggymusic.view.main.myplaylist.MyPlaylistFragment

class FragmentAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> MusicListFragment.newInstance()
            else -> MyPlaylistFragment.newInstance()
        }
    }

    override fun getItemCount(): Int {
        return 2
    }
}