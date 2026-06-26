package com.hero.ziggymusic.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.data.local.entity.FavoriteMusicTrackEntity
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity

@Dao
interface FavoriteMusicTracksDao {
    @Query("""
        SELECT music_tracks.*
        FROM music_tracks
        INNER JOIN favorite_music_tracks
        ON music_tracks.id = favorite_music_tracks.id
        ORDER BY favorite_music_tracks.created_at DESC
    """)
    fun getFavoriteMusicTracks(): LiveData<List<MusicTrackEntity>>

    @Query("SELECT id FROM favorite_music_tracks")
    fun getFavoriteMusicTrackIdList(): LiveData<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteMusicTrack(favoriteMusicTrack: FavoriteMusicTrackEntity)

    @Query("DELETE FROM favorite_music_tracks WHERE id = :id")
    suspend fun deleteFavoriteMusicTrack(id: String)

    @Query("DELETE FROM favorite_music_tracks WHERE id NOT IN (:musicTrackIdList)")
    suspend fun deleteFavoriteTracksExcept(musicTrackIdList: List<String>)

    @Query("DELETE FROM favorite_music_tracks")
    suspend fun clearAll()
}
