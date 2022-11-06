package com.hero.ziggymusic

import com.hero.ziggymusic.database.local.MusicLocalDataSourceImpl
import com.hero.ziggymusic.database.AppMusicDatabase
import com.hero.ziggymusic.database.AppMyPlaylistDatabase
import com.hero.ziggymusic.database.music.repository.MusicRepositoryImpl
import com.hero.ziggymusic.database.music.repository.MusicRepository

object Injector {

    fun provideMusicRepository(): MusicRepository =
        MusicRepositoryImpl(
            MusicLocalDataSourceImpl(
            ZiggyMusicApp.getInstance(),
            AppMusicDatabase.getInstance(ZiggyMusicApp.getInstance()).musicFileDao(), AppMyPlaylistDatabase.getInstance(ZiggyMusicApp.getInstance()).playlistMusicDao())
        )
}