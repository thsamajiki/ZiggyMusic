package com.hero.ziggymusic.database.music.entity

data class PlayerStateHolder (
    private val playMusicList: MutableList<MusicTrackEntity> = mutableListOf()
) {

    var currentMusic: MusicTrackEntity? = null
        private set


    fun replaceMusicList(musicList: List<MusicTrackEntity>) {
        playMusicList.clear()
        playMusicList.addAll(musicList)
    }

    fun updateCurrentMusic(musicTrackEntity: MusicTrackEntity) {
        // 어차피 id 값이 인덱스 값이다.
        currentMusic = musicTrackEntity
    }

    fun changedMusic(newMusicId: String) {
        val newMusic = playMusicList.find {
            it.id == newMusicId
        }

        if (newMusic != null) {
            currentMusic = newMusic
        }
    }

    fun clearCurrentMusic() {
        currentMusic = null
    }

    companion object {
        private var instance: PlayerStateHolder? = null

        fun getInstance(): PlayerStateHolder = instance
            ?: PlayerStateHolder().apply { instance = this }
    }
}