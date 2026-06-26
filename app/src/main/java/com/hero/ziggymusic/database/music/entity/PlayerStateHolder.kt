package com.hero.ziggymusic.database.music.entity

data class PlayerStateHolder (
    private val musicTrackList: MutableList<MusicTrackEntity> = mutableListOf()
) {
    var currentMusicTrack: MusicTrackEntity? = null
        private set


    fun replaceMusicTrackList(trackList: List<MusicTrackEntity>) {
        musicTrackList.clear()
        musicTrackList.addAll(trackList)
    }

    fun updateCurrentMusic(musicTrack: MusicTrackEntity) {
        // 어차피 id 값이 인덱스 값이다.
        currentMusicTrack = musicTrack
    }

    fun changedMusicTrack(newTrackId: String) {
        val newMusicTrack = musicTrackList.find {
            it.id == newTrackId
        }

        if (newMusicTrack != null) {
            currentMusicTrack = newMusicTrack
        }
    }

    fun clearCurrentMusicTrack() {
        currentMusicTrack = null
    }

    companion object {
        private var instance: PlayerStateHolder? = null

        fun getInstance(): PlayerStateHolder = instance
            ?: PlayerStateHolder().apply { instance = this }
    }
}