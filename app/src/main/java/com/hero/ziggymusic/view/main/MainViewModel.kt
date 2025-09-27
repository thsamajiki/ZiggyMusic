package com.hero.ziggymusic.view.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import com.hero.ziggymusic.view.main.model.MainTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicRepository: MusicRepository
): ViewModel() {

    val musicList: LiveData<List<MusicModel>> = musicRepository.getAllMusic()

    // MainTitle 상태 관리
    private val _currentTitle = MutableLiveData<MainTitle>(MainTitle.MusicList)
    val currentTitle: LiveData<MainTitle> = _currentTitle

    fun setTitle(title: MainTitle) {
        _currentTitle.value = title
    }
}
