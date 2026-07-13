package com.hero.ziggymusic.domain.music.model

enum class MusicTracksSortOrder {
    TITLE_ASCENDING,
    TITLE_DESCENDING,
    ARTIST_ASCENDING,
    ARTIST_DESCENDING,
    DATE_ADDED_ASCENDING,
    DATE_ADDED_DESCENDING;

    val isTitleOrder: Boolean
        get() = this == TITLE_ASCENDING || this == TITLE_DESCENDING

    val isArtistOrder: Boolean
        get() = this == ARTIST_ASCENDING || this == ARTIST_DESCENDING

    val isDateAddedOrder: Boolean
        get() = this == DATE_ADDED_ASCENDING || this == DATE_ADDED_DESCENDING

    val isDescending: Boolean
        get() = this == TITLE_DESCENDING || this == ARTIST_DESCENDING || this == DATE_ADDED_DESCENDING
}
