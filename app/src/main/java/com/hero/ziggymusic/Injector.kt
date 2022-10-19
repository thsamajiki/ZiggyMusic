package com.hero.ziggymusic

import com.hero.ziggymusic.data.music.local.MusicLocalDataSourceImpl
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.music.repository.MusicRepositoryImpl
import com.hero.ziggymusic.domain.music.repository.MusicRepository

object Injector {

    fun provideMusicRepository(): MusicRepository =
        MusicRepositoryImpl(
            MusicLocalDataSourceImpl(
            ZiggyMusicApp.getInstance(),
            AppMusicDatabase.getInstance(ZiggyMusicApp.getInstance()).musicFileDao())
        )
}