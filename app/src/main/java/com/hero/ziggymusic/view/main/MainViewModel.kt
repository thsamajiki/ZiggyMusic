package com.hero.ziggymusic.view.main

import androidx.lifecycle.ViewModel
import com.hero.ziggymusic.database.music.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val musicRepository: MusicRepository
): ViewModel() {
    
    
}