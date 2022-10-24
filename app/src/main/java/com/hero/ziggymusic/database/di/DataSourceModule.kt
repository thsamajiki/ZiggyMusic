package com.hero.ziggymusic.database.di

import com.hero.ziggymusic.data.music.local.MusicLocalDataSource
import com.hero.ziggymusic.data.music.local.MusicLocalDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.text.Typography.dagger

@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    @Singleton
    @Binds
    abstract fun bindMusicLocalDataSource(
        musicLocalDataSourceImpl: MusicLocalDataSourceImpl
    ): MusicLocalDataSource
}