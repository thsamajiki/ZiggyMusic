package com.hero.ziggymusic.presentation.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import com.hero.ziggymusic.presentation.main.model.MainTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicRepository: MusicRepository
): ViewModel() {
    val musicTracks: LiveData<List<MusicTrackEntity>> = musicRepository.observeMusicTracks()

    // MainTitle 상태 관리
    private val _currentTitle = MutableLiveData<MainTitle>(MainTitle.MusicTracks)
    val currentTitle: LiveData<MainTitle> = _currentTitle

    fun setTitle(title: MainTitle) {
        _currentTitle.value = title
    }
}
