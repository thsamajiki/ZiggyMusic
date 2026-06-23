package com.hero.ziggymusic.database.music.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.database.music.entity.MusicModel

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM music_table ORDER BY id ASC")
    fun getAllFiles() : LiveData<List<MusicModel>>

    @Query("SELECT * FROM music_table WHERE id = :key limit 1")
    suspend fun getMusicFileFromKey(key: String?): MusicModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusic(musicModel: MusicModel)

    @Delete
    suspend fun deleteMusic(musicModel: MusicModel)

    @Query("DELETE FROM music_table WHERE id NOT IN (:musicIdList)")
    suspend fun deleteFavoritesExcept(musicIdList: List<String>)

    @Query("DELETE FROM music_table")
    suspend fun clearAll()
}
