package com.hero.ziggymusic.database.music.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hero.ziggymusic.database.music.entity.FavoriteMusicEntity
import com.hero.ziggymusic.database.music.entity.MusicModel

@Dao
interface FavoritesDao {
    @Query("""
        SELECT music.*
        FROM music
        INNER JOIN favorite_music
        ON music.id = favorite_music.id
        ORDER BY favorite_music.created_at DESC
    """)
    fun getFavoriteMusicList(): LiveData<List<MusicModel>>

    @Query("SELECT id FROM favorite_music")
    fun getFavoriteMusicIdList(): LiveData<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteMusic(favoriteMusic: FavoriteMusicEntity)

    @Query("DELETE FROM favorite_music WHERE id = :id")
    suspend fun deleteFavoriteMusic(id: String)

    @Query("DELETE FROM favorite_music WHERE id NOT IN (:musicIdList)")
    suspend fun deleteFavoritesExcept(musicIdList: List<String>)

    @Query("DELETE FROM favorite_music")
    suspend fun clearAll()
}
