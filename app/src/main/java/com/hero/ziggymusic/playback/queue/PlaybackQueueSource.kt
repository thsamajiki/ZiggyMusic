package com.hero.ziggymusic.playback.queue

/*
** 재생을 시작한 목록을 구분해 이후 큐 동기화에도 같은 출처를 사용한다.
*/
enum class PlaybackQueueSource {
    MUSIC_TRACKS,
    FAVORITE_MUSIC_TRACKS
}
