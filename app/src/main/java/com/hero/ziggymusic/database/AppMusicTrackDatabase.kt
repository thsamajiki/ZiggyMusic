package com.hero.ziggymusic.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hero.ziggymusic.database.music.dao.FavoriteTracksDao
import com.hero.ziggymusic.database.music.dao.MusicTrackDao
import com.hero.ziggymusic.database.music.entity.FavoriteTrackEntity
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity

@Database(entities = [MusicTrackEntity::class, FavoriteTrackEntity::class], version = 1, exportSchema = false)
abstract class AppMusicTrackDatabase : RoomDatabase() {
    abstract fun musicFileDao(): MusicTrackDao
    abstract fun favoritesDao(): FavoriteTracksDao

    companion object {
        @Volatile
        private var instance: AppMusicTrackDatabase? = null

        fun getInstance(context: Context): AppMusicTrackDatabase = instance ?: synchronized(this) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppMusicTrackDatabase::class.java, "ziggy_music.db"
            ).build()
    }
}