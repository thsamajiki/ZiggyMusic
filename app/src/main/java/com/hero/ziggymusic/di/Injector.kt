package com.hero.ziggymusic.di

import com.hero.ziggymusic.ZiggyMusicApp
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.AppFavoritesDatabase
import com.hero.ziggymusic.database.local.MusicLocalDataSourceImpl
import com.hero.ziggymusic.domain.music.repository.MusicRepository
import com.hero.ziggymusic.database.music.repository.MusicRepositoryImpl
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object Injector {
    fun provideMusicRepository(): MusicRepository =
        MusicRepositoryImpl(
            MusicLocalDataSourceImpl(
                ZiggyMusicApp.Companion.getInstance(),
                AppMusicDatabase.Companion.getInstance(ZiggyMusicApp.Companion.getInstance()),
                AppMusicDatabase.Companion.getInstance(ZiggyMusicApp.Companion.getInstance())
                    .musicFileDao(),
                AppFavoritesDatabase.Companion.getInstance(ZiggyMusicApp.Companion.getInstance())
                    .favoritesDao()
            )
        )
}