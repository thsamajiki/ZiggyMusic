package com.hero.ziggymusic.database.music.entity

data class PlayerModel (
    private val playMusicList: MutableList<MusicModel> = mutableListOf()
) {

    var currentMusic: MusicModel? = null
        private set


    fun replaceMusicList(musicList: List<MusicModel>) {
        playMusicList.clear()
        playMusicList.addAll(musicList)
    }

    fun updateCurrentMusic(musicModel: MusicModel) {
        // 어차피 id 값이 인덱스 값이다.
        currentMusic = musicModel
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
        private var instance: PlayerModel? = null

        fun getInstance(): PlayerModel = instance
            ?: PlayerModel().apply { instance = this }
    }
}