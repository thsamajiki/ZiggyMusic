package com.hero.ziggymusic.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.data.local.entity.FavoriteTrackEntity
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity

@Dao
interface FavoriteTracksDao {
    @Query("""
        SELECT music_tracks.*
        FROM music_tracks
        INNER JOIN favorite_music
        ON music_tracks.id = favorite_music.id
        ORDER BY favorite_music.created_at DESC
    """)
    fun getFavoriteMusicTracks(): LiveData<List<MusicTrackEntity>>

    @Query("SELECT id FROM favorite_music")
    fun getFavoriteMusicTrackIdList(): LiveData<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteMusicTrack(favoriteMusic: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_music WHERE id = :id")
    suspend fun deleteFavoriteMusicTrack(id: String)

    @Query("DELETE FROM favorite_music WHERE id NOT IN (:musicTrackIdList)")
    suspend fun deleteFavoriteTracksExcept(musicTrackIdList: List<String>)

    @Query("DELETE FROM favorite_music")
    suspend fun clearAll()
}
