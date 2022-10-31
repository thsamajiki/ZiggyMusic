package com.hero.ziggymusic.database.music.entity

data class PlayerModel (
    private val playMusicList: MutableList<MusicModel> = mutableListOf()
) {

    var currentMusic: MusicModel? = null
        private set // private set 키워드를 변수선언 아래에 붙이면 외부에 setter를 허용하지 않을 수 있다.


    fun replaceMusicList(musicList: List<MusicModel>) {
        playMusicList.clear()
        playMusicList.addAll(musicList)
    }

    fun updateCurrentMusic(musicModel: MusicModel) {
//        어차피 id 값이 인덱스 값이다.
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