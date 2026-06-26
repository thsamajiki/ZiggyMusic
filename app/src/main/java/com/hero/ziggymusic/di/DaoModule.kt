package com.hero.ziggymusic.di

import android.app.Application
import com.hero.ziggymusic.data.local.db.AppMusicTrackDatabase
import com.hero.ziggymusic.data.local.dao.MusicTrackDao
import com.hero.ziggymusic.data.local.dao.FavoriteTracksDao
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
    fun provideMusicDao(appMusicTrackDatabase: AppMusicTrackDatabase): MusicTrackDao {
        return appMusicTrackDatabase.musicFileDao()
    }

    @Singleton
    @Provides
    fun provideAppMusicDatabase(application: Application): AppMusicTrackDatabase {
        return AppMusicTrackDatabase.getInstance(application)
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(appMusicTrackDatabase: AppMusicTrackDatabase): FavoriteTracksDao {
        return appMusicTrackDatabase.favoritesDao()
    }
}
