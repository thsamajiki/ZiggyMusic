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
        currentMusic = musicModel
    }

    fun changedMusic(newMusicKey: String) {
        val newMusic = playMusicList.find {
            it.id == newMusicKey
        }

        if (newMusic != null) {
            currentMusic = newMusic
        }
    }
}