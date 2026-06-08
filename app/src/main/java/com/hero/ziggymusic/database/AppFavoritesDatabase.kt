package com.hero.ziggymusic.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hero.ziggymusic.database.music.dao.FavoritesDao
import com.hero.ziggymusic.database.music.entity.MusicModel

@Database(entities = [MusicModel::class], version = 1, exportSchema = false)
abstract class AppFavoritesDatabase: RoomDatabase() {

    abstract fun favoritesDao(): FavoritesDao

    companion object {
        @Volatile
        private var instance: AppFavoritesDatabase? = null

        fun getInstance(context: Context): AppFavoritesDatabase = instance ?: synchronized(this) {
            instance ?: buildDatabase(context).also { instance = it }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppFavoritesDatabase::class.java, "favorites_db"
            ).build()
    }
}