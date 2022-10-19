package com.hero.ziggymusic.domain.music.usecase

import com.hero.ziggymusic.domain.music.repository.MusicRepository

class GetMyPlayListUseCase {
    private lateinit var musicRepository : MusicRepository

    fun GetMyPlayListUseCase(musicRepository: MusicRepository?) {
        this.musicRepository = musicRepository!!
    }

//    fun invoke(onCompleteListener: OnCompleteListener<List<MusicModel>>) {
//        musicRepository.getMyPlayList(onCompleteListener)
//    }
}