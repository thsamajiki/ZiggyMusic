package com.hero.ziggymusic.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hero.ziggymusic.data.local.dao.FavoriteMusicTracksDao
import com.hero.ziggymusic.data.local.dao.MusicTrackDao
import com.hero.ziggymusic.data.local.entity.FavoriteMusicTrackEntity
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity

@Database(entities = [MusicTrackEntity::class, FavoriteMusicTrackEntity::class], version = 1, exportSchema = false)
abstract class AppMusicTrackDatabase : RoomDatabase() {
    abstract fun musicFileDao(): MusicTrackDao
    abstract fun favoritesDao(): FavoriteMusicTracksDao

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