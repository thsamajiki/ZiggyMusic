package com.hero.ziggymusic.di

import com.hero.ziggymusic.data.music.source.MusicLocalDataSource
import com.hero.ziggymusic.data.music.source.MusicLocalDataSourceImpl
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
