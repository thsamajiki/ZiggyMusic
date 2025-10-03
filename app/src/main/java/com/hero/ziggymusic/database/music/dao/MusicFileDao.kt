package com.hero.ziggymusic.database.music.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.database.music.entity.MusicModel

@Dao
interface MusicFileDao {
    @Query("SELECT * FROM music_table ORDER BY title ASC")
    fun getAllFiles() : LiveData<List<MusicModel>>

    @Query("SELECT * FROM music_table WHERE id = :key limit 1")
    suspend fun getMusicFileFromKey(key: String?): MusicModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMusic(musicModel: MusicModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(musicList: List<MusicModel>)

    @Delete
    fun deleteMusic(musicModel: MusicModel)
}