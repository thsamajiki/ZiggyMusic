package com.hero.ziggymusic.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hero.ziggymusic.database.music.dao.MusicFileDao
import com.hero.ziggymusic.database.music.entity.MusicModel

@Database(entities = [MusicModel::class], version = 1)
abstract class AppMusicDatabase : RoomDatabase() {

    abstract fun musicFileDao(): MusicFileDao

    companion object {
        @Volatile
        private var instance: AppMusicDatabase? = null

        fun getInstance(context: Context): AppMusicDatabase = instance ?: synchronized(this) { // singleton pattern
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppMusicDatabase::class.java, "music_table.db"
            ).build()
    }
}