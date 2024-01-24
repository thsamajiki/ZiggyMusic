package com.hero.ziggymusic.view.main.myplaylist.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hero.ziggymusic.R
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

//    init {
//        viewModelScope.launch {
//            musicRepository.loadMusics()
//        }
//    }

    val myPlaylist : LiveData<List<MusicModel>> = musicRepository.getMyPlaylistMusics()

    private val _emptyStateMessage =
        MutableLiveData(getApplication<Application>().getString(R.string.no_music_found))
    val emptyStateMessage: LiveData<String>
        get() = _emptyStateMessage

    fun deleteMusicFromMyPlaylist(musicModel: MusicModel) {
        viewModelScope.launch {
            musicRepository.deleteMusicFromMyPlaylist(musicModel)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}