package com.hero.ziggymusic.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hero.ziggymusic.database.music.dao.PlaylistMusicDao
import com.hero.ziggymusic.database.music.entity.MusicModel

@Database(entities = [MusicModel::class], version = 1, exportSchema = false)
abstract class AppMyPlaylistDatabase: RoomDatabase() {

    abstract fun playlistMusicDao(): PlaylistMusicDao

    companion object {
        @Volatile
        private var instance: AppMyPlaylistDatabase? = null

        fun getInstance(context: Context): AppMyPlaylistDatabase = instance ?: synchronized(this) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppMyPlaylistDatabase::class.java, "my_playlist_db"
            ).build()
    }
}