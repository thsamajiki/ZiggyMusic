package com.hero.ziggymusic.database.music.entity

data class PlayerModel (
    private val playMusicList: MutableList<MusicModel> = mutableListOf()
) {

    var currentMusic: MusicModel? = null // -1: 초기화 되지 않은 값
        private set

    private val currentMusicKey: String
        get() = currentMusic?.id.orEmpty()

    var isWatchingPlayListView: Boolean = true
        private set

    fun replaceMusicList(musicList: List<MusicModel>) {
        playMusicList.clear()
        playMusicList.addAll(musicList)
    }

    // 가져 갈 때마다 position 위치 보고 반환
    fun getAdapterModels(): List<MusicModel> {
        return playMusicList.map { musicModel ->
            // data class 의 강력한 기능 중 하나 copy. 수정하려는 값만 수정.
            /*
            copy() 를 통해 새로운 값만 갱신
            copy() 를 통해 리스트를 갱신하여 리사이클러뷰에 리스트 등록 시
            변경된 값만 UI 갱신 하도록 할 수 있었음
             */
            val newItem = musicModel.copy(
                isPlaying = musicModel.id == currentMusicKey
            )
            newItem
        }
    }

    fun updateCurrentMusic(musicModel: MusicModel) {
        /*
        어차피 id 값이 인덱스 값이긴 함.
         */
        currentMusic = musicModel
    }

    fun nextMusic(): MusicModel? {
        if (playMusicList.isEmpty()) return null

        val playIndex = playMusicList.indexOf(currentMusic)

        val nextMusic = playMusicList.getOrNull(playIndex + 1)
        if(nextMusic != null) {
            currentMusic = nextMusic
        }

        return currentMusic
    }

    fun prevMusic(): MusicModel? {
        if (playMusicList.isEmpty()) return null

        val playIndex = playMusicList.indexOf(currentMusic)

        val prevMusic = playMusicList.getOrNull(playIndex - 1)
        if(prevMusic != null) {
            currentMusic = prevMusic
        }

        return currentMusic
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