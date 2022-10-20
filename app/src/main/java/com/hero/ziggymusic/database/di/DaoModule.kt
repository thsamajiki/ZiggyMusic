package com.hero.ziggymusic.database.di

import android.app.Application
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.music.dao.MusicFileDao
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
}