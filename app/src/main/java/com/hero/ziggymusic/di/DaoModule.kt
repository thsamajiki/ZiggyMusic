package com.hero.ziggymusic.di

import android.app.Application
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.music.dao.MusicTrackDao
import com.hero.ziggymusic.database.music.dao.FavoriteTracksDao
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
    fun provideMusicDao(appMusicDatabase: AppMusicDatabase): MusicTrackDao {
        return appMusicDatabase.musicFileDao()
    }

    @Singleton
    @Provides
    fun provideAppMusicDatabase(application: Application): AppMusicDatabase {
        return AppMusicDatabase.getInstance(application)
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(appMusicDatabase: AppMusicDatabase): FavoriteTracksDao {
        return appMusicDatabase.favoritesDao()
    }
}
