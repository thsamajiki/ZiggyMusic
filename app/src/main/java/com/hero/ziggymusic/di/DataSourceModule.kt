package com.hero.ziggymusic.di

import com.hero.ziggymusic.database.local.MusicLocalDataSource
import com.hero.ziggymusic.database.local.MusicLocalDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {
    @Singleton
    @Binds
    abstract fun bindMusicLocalDataSource(
        musicLocalDataSourceImpl: MusicLocalDataSourceImpl
    ): MusicLocalDataSource
}
