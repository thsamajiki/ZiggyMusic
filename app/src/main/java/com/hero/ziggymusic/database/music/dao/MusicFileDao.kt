package com.hero.ziggymusic.database.music.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hero.ziggymusic.database.BaseDao
import com.hero.ziggymusic.database.music.entity.MusicFileData
import com.hero.ziggymusic.database.music.entity.MusicModel

@Dao
interface MusicFileDao : BaseDao<MusicFileData> {
    @Query("SELECT * FROM music_table ORDER BY id ASC")
    fun getAllFiles() : LiveData<List<MusicModel>>

    @Query("SELECT * FROM music_table WHERE id = :key limit 1")
    fun getMusicFileFromKey(key: String?): MusicFileData?

    @Insert
    fun insertMusic(musicModel: MusicModel)
}