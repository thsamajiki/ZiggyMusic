package com.hero.ziggymusic.database.music.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity

@Dao
interface MusicTrackDao {
    @Query("SELECT * FROM music ORDER BY title ASC")
    fun getAllFiles() : LiveData<List<MusicTrackEntity>>

    @Query("SELECT COUNT(*) FROM music")
    suspend fun getMusicCount(): Int

    @Query("SELECT * FROM music WHERE id = :key limit 1")
    suspend fun getMusicFileFromKey(key: String?): MusicTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMusic(musicTrackEntity: MusicTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(musicList: List<MusicTrackEntity>)

    @Query("DELETE FROM music")
    suspend fun clearAll()

    @Delete
    fun deleteMusic(musicTrackEntity: MusicTrackEntity)

    @Query("DELETE FROM music WHERE id NOT IN (:musicIdList)")
    suspend fun deleteFilesExcept(musicIdList: List<String>)
}
