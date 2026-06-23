package com.hero.ziggymusic.database.music.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.database.music.entity.MusicModel

@Dao
interface MusicFileDao {
    @Query("SELECT * FROM music ORDER BY title ASC")
    fun getAllFiles() : LiveData<List<MusicModel>>

    @Query("SELECT COUNT(*) FROM music")
    suspend fun getMusicCount(): Int

    @Query("SELECT * FROM music WHERE id = :key limit 1")
    suspend fun getMusicFileFromKey(key: String?): MusicModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMusic(musicModel: MusicModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(musicList: List<MusicModel>)

    @Query("DELETE FROM music")
    suspend fun clearAll()

    @Delete
    fun deleteMusic(musicModel: MusicModel)

    @Query("DELETE FROM music WHERE id NOT IN (:musicIdList)")
    suspend fun deleteFilesExcept(musicIdList: List<String>)
}
