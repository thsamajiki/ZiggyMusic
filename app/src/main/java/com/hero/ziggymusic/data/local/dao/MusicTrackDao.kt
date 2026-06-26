package com.hero.ziggymusic.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity

@Dao
interface MusicTrackDao {
    @Query("SELECT * FROM music_tracks ORDER BY title ASC")
    fun getMusicTracksFromMediaStore() : LiveData<List<MusicTrackEntity>>

    @Query("SELECT COUNT(*) FROM music_tracks")
    suspend fun getMusicTrackCount(): Int

    @Query("SELECT * FROM music_tracks WHERE id = :id limit 1")
    suspend fun getMusicTrack(id: String?): MusicTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMusicTracks(musicTrackList: List<MusicTrackEntity>)

    @Query("DELETE FROM music_tracks")
    suspend fun clearAll()

    @Delete
    fun deleteMusicTrack(musicTrackEntity: MusicTrackEntity)

    @Query("DELETE FROM music_tracks WHERE id NOT IN (:musicTrackIdList)")
    suspend fun deleteMusicTracksExcept(musicTrackIdList: List<String>)
}
