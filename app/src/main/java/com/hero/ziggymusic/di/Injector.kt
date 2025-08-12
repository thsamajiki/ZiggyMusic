package com.hero.ziggymusic.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.hero.ziggymusic.ZiggyMusicApp
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.AppMyPlaylistDatabase
import com.hero.ziggymusic.database.local.MusicLocalDataSourceImpl
import com.hero.ziggymusic.database.music.repository.MusicRepository
import com.hero.ziggymusic.database.music.repository.MusicRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Injector {
    fun provideMusicRepository(): MusicRepository =
        MusicRepositoryImpl(
            MusicLocalDataSourceImpl(
                ZiggyMusicApp.Companion.getInstance(),
                AppMusicDatabase.Companion.getInstance(ZiggyMusicApp.Companion.getInstance())
                    .musicFileDao(),
                AppMyPlaylistDatabase.Companion.getInstance(ZiggyMusicApp.Companion.getInstance())
                    .playlistMusicDao()
            )
        )
}