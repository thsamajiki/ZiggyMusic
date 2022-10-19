package com.hero.ziggymusic.domain.music.usecase

import com.hero.ziggymusic.domain.music.repository.MusicRepository

class GetMusicListUseCase {
    private lateinit var musicRepository : MusicRepository

    fun GetMusicListUseCase(musicRepository: MusicRepository) {
        this.musicRepository = musicRepository
    }

//    fun invoke(onCompleteListener: OnCompleteListener<List<MusicModel>>) {
//        musicRepository.getMusicList(onCompleteListener)
//    }
}