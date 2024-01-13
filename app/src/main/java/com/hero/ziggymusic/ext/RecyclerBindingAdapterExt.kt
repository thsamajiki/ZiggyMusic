package com.hero.ziggymusic.ext

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.view.main.musiclist.MusicListAdapter
import com.hero.ziggymusic.view.main.myplaylist.MyPlaylistAdapter

@BindingAdapter("musicListItems")
fun RecyclerView.setMusicListItems(items: List<MusicModel>?) {
    items ?: return

    val adapter = this.adapter as? MusicListAdapter
    adapter?.setMusicList(items)
}

@BindingAdapter("myPlaylistItems")
fun RecyclerView.setMyPlaylistItems(items: List<MusicModel>?) {
    items ?: return

    val adapter = this.adapter as? MyPlaylistAdapter
    adapter?.setMusicList(items)
}