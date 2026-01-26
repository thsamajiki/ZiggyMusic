package com.hero.ziggymusic.view.main.player

enum class PlaybackContentType(
    val idKey: String,
    val positionKey: String,
    val playWhenReadyKey: String
) {
    MUSIC(
        idKey = "last_music_id",
        positionKey = "last_music_position_ms",
        playWhenReadyKey = "last_music_play_when_ready"
    ),
    RADIO(
        idKey = "last_radio_id",
        positionKey = "last_radio_position_ms",
        playWhenReadyKey = "last_radio_play_when_ready"
    ),
    PODCAST(
        idKey = "last_podcast_id",
        positionKey = "last_podcast_position_ms",
        playWhenReadyKey = "last_podcast_play_when_ready"
    )
}
