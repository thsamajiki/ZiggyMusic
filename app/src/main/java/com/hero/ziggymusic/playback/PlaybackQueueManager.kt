package com.hero.ziggymusic.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.hero.ziggymusic.database.music.entity.MusicModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val player: ExoPlayer
) {
    @OptIn(UnstableApi::class)
    fun prepareQueue(
        musicList: List<MusicModel>,
        selectedMusic: MusicModel?,
        startPositionMs: Long = 0L
    ) {
        if (musicList.isEmpty()) return

        // 마지막으로 재생한 곡이 목록에 있으면 해당 위치에서 큐를 시작한다.
        val startIndex = selectedMusic
            ?.let { music -> musicList.indexOfFirst { it.id == music.id } }
            ?.takeIf { it >= 0 }
            ?: 0

        val mediaSources = musicList.map { music ->
            music.toProgressiveMediaSource(context)
        }

        player.setMediaSources(
            mediaSources,
            startIndex,
            startPositionMs.coerceAtLeast(0L)
        )
        player.prepare()
    }

    @OptIn(UnstableApi::class)
    fun syncQueue(musicList: List<MusicModel>) {
        if (musicList.isEmpty()) {
            // 재생 가능한 음원이 없으면 기존 큐를 정리하여 삭제된 항목이 남지 않게 한다.
            if (player.mediaItemCount > 0) {
                player.pause()
                player.clearMediaItems()
            }
            return
        }

        val latestMediaIds = musicList.map { it.id }
        val currentMediaIds = player.currentMediaIds()

        if (currentMediaIds == latestMediaIds) return

        // 큐를 다시 구성하기 전에 현재 재생 상태를 저장해 복원 여부를 판단한다.
        val currentMediaId = player.currentMediaItem?.mediaId

        val currentItemStillExists = currentMediaId != null && latestMediaIds.contains(currentMediaId)

        if (currentItemStillExists && canUpdateQueue(currentMediaIds, latestMediaIds)) {
            updateQueue(
                currentMediaIds = currentMediaIds,
                latestMusicList = musicList
            )
            return
        }

        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.isPlaying

        val restoredIndex = currentMediaId
            ?.let { mediaId -> musicList.indexOfFirst { it.id == mediaId } }
            ?.takeIf { it >= 0 }

        val targetIndex = restoredIndex ?: 0
        val targetPositionMs = if (restoredIndex != null) {
            currentPositionMs
        } else {
            // 현재 곡이 목록에서 사라졌다면 다른 곡의 중간 위치로 복원하지 않는다.
            0L
        }

        val shouldResumePlayback = wasPlaying && restoredIndex != null

        val mediaSources = musicList.map { music ->
            music.toProgressiveMediaSource(context)
        }

        player.setMediaSources(mediaSources, targetIndex, targetPositionMs)
        player.prepare()

        if (shouldResumePlayback) {
            player.play()
        }
    }

    // 현재 재생 중인 곡을 유지할 수 있고 기존 곡들의 상대적인 순서가 바뀌지 않았다면
    // Player 큐를 전체 재생성하지 않고 추가/삭제된 항목만 반영할 수 있다.
    private fun canUpdateQueue(
        currentIds: List<String>,
        latestIds: List<String>
    ): Boolean {
        val currentIdSet = currentIds.toSet()
        val latestIdSet = latestIds.toSet()

        val currentIdsStillInLatest = currentIds.filter { it in latestIdSet }
        val latestIdsAlreadyInCurrent = latestIds.filter { it in currentIdSet }

        return currentIdsStillInLatest == latestIdsAlreadyInCurrent
    }

    // 전체 큐를 다시 준비하면 재생이 잠깐 끊길 수 있으므로,
    // 삭제된 항목은 제거하고 새로 추가된 항목만 현재 Player 큐에 반영한다.
    @OptIn(UnstableApi::class)
    private fun updateQueue(
        currentMediaIds: List<String>,
        latestMusicList: List<MusicModel>
    ) {
        val latestMediaIds = latestMusicList.map { it.id }
        val queueIds = currentMediaIds.toMutableList()

        for (index in queueIds.lastIndex downTo 0) {
            if (queueIds[index] !in latestMediaIds) {
                player.removeMediaItem(index)
                queueIds.removeAt(index)
            }
        }

        latestMusicList.forEachIndexed { targetIndex, music ->
            if (music.id !in queueIds) {
                player.addMediaSource(
                    targetIndex,
                    music.toProgressiveMediaSource(context)
                )
                queueIds.add(targetIndex, music.id)
            }
        }
    }

    fun playMusic(
        musicList: List<MusicModel>,
        musicId: String
    ): Boolean {
        // 요청한 musicId가 현재 재생 가능한 음악 목록에 없으면 실패로 처리한다
        if (musicList.none { it.id == musicId }) return false

        // 유효한 재생 요청일 때만 큐를 동기화한다.
        syncQueue(musicList)

        // 큐 동기화 이후 실제 Player 큐에서 선택한 곡의 위치를 다시 찾는다.
        val targetIndex = player.findMediaItemIndexById(musicId)
        if (targetIndex == -1) return false

        player.seekTo(targetIndex, 0L)
        player.play()
        return true
    }

    fun moveToFirstTrackAndPauseIfAtEnd(): String? {
        val isLastTrack = player.currentMediaItemIndex == player.mediaItemCount - 1
        val isRepeatOff = player.repeatMode == Player.REPEAT_MODE_OFF

        if (!isLastTrack || !isRepeatOff || player.mediaItemCount == 0) {
            return null
        }

        // 반복 재생이 꺼진 마지막 곡에서는 첫 곡으로 이동만 하고 자동 재생하지 않는다.
        player.seekTo(0, 0L)
        player.pause()

        return player.currentMediaItem?.mediaId
    }
}
