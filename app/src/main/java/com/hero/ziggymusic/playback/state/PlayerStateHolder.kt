package com.hero.ziggymusic.playback.state

import com.hero.ziggymusic.data.local.entity.MusicTrackEntity

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
        // 전달받은 트랙을 현재 재생 트랙으로 갱신한다.
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