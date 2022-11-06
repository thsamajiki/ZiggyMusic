package com.hero.ziggymusic.database.local

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import com.hero.ziggymusic.database.music.dao.MusicFileDao
import com.hero.ziggymusic.database.music.dao.PlaylistMusicDao
import com.hero.ziggymusic.database.music.entity.MusicModel
import javax.inject.Inject

class MusicLocalDataSourceImpl @Inject constructor(
    private val application: Application,
    private val musicFileDao: MusicFileDao,
    private val playlistMusicDao: PlaylistMusicDao
) : MusicLocalDataSource {

    override suspend fun loadMusics() {
        val musicListUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        val cursor = application.contentResolver.query(
            musicListUri,
            projection,
            null,
            null,
            null
        )

        val musicList = mutableListOf<MusicModel>()

        while (cursor?.moveToNext() == true) {
            val id = cursor.getString(0)
            val title = cursor.getString(1)
            val artist = cursor.getString(2)
            val albumId = cursor.getString(3)
            val duration = cursor.getLong(4)

            val music = MusicModel(id, title, artist, albumId, duration)
            musicList.add(music)
        }

        musicFileDao.insertAll(musicList)
    }

    override suspend fun getMusic(key: String): MusicModel? {
        return musicFileDao.getMusicFileFromKey(key)
    }

    override fun getAllMusic(): LiveData<List<MusicModel>> {
        return musicFileDao.getAllFiles()
    }

    override fun getMyPlaylistMusics(): LiveData<List<MusicModel>> {
        return playlistMusicDao.getAllFiles()
    }

    override suspend fun addMusicToMyPlaylist(musicModel: MusicModel) {
        playlistMusicDao.insertMusic(musicModel)
    }

    override suspend fun deleteMusicFromMyPlaylist(musicModel: MusicModel) {
        playlistMusicDao.deleteMusic(musicModel)
    }
}