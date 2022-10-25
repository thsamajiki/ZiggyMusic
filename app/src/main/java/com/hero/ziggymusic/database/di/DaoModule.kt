package com.hero.ziggymusic.database.di

import android.app.Application
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.AppMyPlaylistDatabase
import com.hero.ziggymusic.database.music.dao.MusicFileDao
import com.hero.ziggymusic.database.music.dao.PlaylistMusicDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DaoModule {

    @Singleton
    @Provides
    fun provideMusicDao(appMusicDatabase: AppMusicDatabase): MusicFileDao {
        return appMusicDatabase.musicFileDao()
    }

    @Singleton
    @Provides
    fun provideAppMusicDatabase(application: Application): AppMusicDatabase {
        return AppMusicDatabase.getInstance(application)
    }

    @Singleton
    @Provides
    fun provideMyPlaylistDao(appMyPlaylistDatabase: AppMyPlaylistDatabase): PlaylistMusicDao {
        return appMyPlaylistDatabase.playlistMusicDao()
    }

    @Singleton
    @Provides
    fun provideAppMyPlaylistDatabase(application: Application): AppMyPlaylistDatabase {
        return AppMyPlaylistDatabase.getInstance(application)
    }
}