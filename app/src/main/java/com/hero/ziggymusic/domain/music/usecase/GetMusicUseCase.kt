package com.hero.ziggymusic.domain.music.usecase

import com.hero.ziggymusic.domain.music.repository.MusicRepository

class GetMusicUseCase {
    private lateinit var musicRepository : MusicRepository

    fun GetMusicUseCase(musicRepository: MusicRepository?) {
        this.musicRepository = musicRepository!!
    }

//    fun invoke(key: String) {
//        musicRepository.getMusic(key)
//    }
}