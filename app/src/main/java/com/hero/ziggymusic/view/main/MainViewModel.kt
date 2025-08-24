package com.hero.ziggymusic.view.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.database.music.entity.MusicModel
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicRepository: MusicRepository
): ViewModel() {

    val musicList: LiveData<List<MusicModel>> = musicRepository.getAllMusic()
}