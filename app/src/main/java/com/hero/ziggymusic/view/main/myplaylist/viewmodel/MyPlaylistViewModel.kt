package com.hero.ziggymusic.view.main.myplaylist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.database.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyPlaylistViewModel @Inject constructor(
    application: Application,
    private val musicRepository: MusicRepository
) : AndroidViewModel(application) {

    val myPlaylist : LiveData<List<MusicModel>> = musicRepository.getMyPlaylistMusics()

    fun deleteMusicFromMyPlaylist(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.deleteMusicFromMyPlaylist(musicModel)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}