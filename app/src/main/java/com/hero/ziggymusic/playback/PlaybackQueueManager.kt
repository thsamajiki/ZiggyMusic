package com.hero.ziggymusic.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.hero.ziggymusic.database.music.entity.MusicTrackEntity
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
        musicTrackList: List<MusicTrackEntity>,
        selectedMusic: MusicTrackEntity?,
        startPositionMs: Long = 0L
    ) {
        if (musicTrackList.isEmpty()) return

        // 마지막으로 재생한 곡이 목록에 있으면 해당 위치에서 큐를 시작한다.
        val startIndex = selectedMusic
            ?.let { music -> musicTrackList.indexOfFirst { it.id == music.id } }
            ?.takeIf { it >= 0 }
            ?: 0

        val mediaSources = musicTrackList.map { music ->
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
    fun syncQueue(musicTrackList: List<MusicTrackEntity>): PlaybackQueueSyncResult {
        // 큐를 갱신하기 전에 현재 곡과 재생 상태를 저장해 삭제 후에도 자연스럽게 이어간다.
        val previousTrackIds = player.currentMediaIds()
        val previousTrackId = player.currentMediaItem?.mediaId
        val removedTrackIndex = previousTrackIds.indexOf(previousTrackId)
        val wasPlaying = player.isPlaying || player.playWhenReady

        if (musicTrackList.isEmpty()) {
            // 재생 가능한 음원이 없으면 기존 큐를 정리하여 삭제된 항목이 남지 않게 한다.
            if (player.mediaItemCount > 0) {
                player.pause()
                player.clearMediaItems()

                return PlaybackQueueSyncResult(
                    selectedMediaId = null,
                    queueChanged = true
                )
            }

            return PlaybackQueueSyncResult(
                selectedMediaId = null,
                queueChanged = false
            )
        }

        val latestTrackIds = musicTrackList.map { it.id }

        if (previousTrackIds == latestTrackIds) {
            return PlaybackQueueSyncResult(
                selectedMediaId = previousTrackId,
                queueChanged = false
            )
        }

        val currentItemStillExists =
            previousTrackId != null && previousTrackId in latestTrackIds

        if (currentItemStillExists && canUpdateQueue(previousTrackIds, latestTrackIds)) {
            updateQueue(
                currentMediaIds = previousTrackIds,
                latestTrackList = musicTrackList
            )

            return PlaybackQueueSyncResult(
                selectedMediaId = previousTrackId,
                queueChanged = true
            )
        }

        val restoredIndex = previousTrackId
            ?.let { mediaId -> musicTrackList.indexOfFirst { it.id == mediaId } }
            ?.takeIf { it >= 0 }

        // 현재 곡이 삭제된 경우 같은 위치의 다음 곡을, 마지막 곡이었다면 이전 곡을 선택한다.
        val replacementIndex = removedTrackIndex
            .takeIf { it >= 0 }
            ?.coerceAtMost(musicTrackList.lastIndex)
            ?: 0

        val targetIndex = restoredIndex ?: replacementIndex
        val targetPositionMs = if (restoredIndex != null) {
            player.currentPosition.coerceAtLeast(0L)
        } else {
            // 현재 곡이 목록에서 사라졌다면 다른 곡의 중간 위치로 복원하지 않는다.
            0L
        }

        val mediaSources = musicTrackList.map { musicTrack ->
            musicTrack.toProgressiveMediaSource(context)
        }

        // 큐 재구성 중 의도치 않은 자동 재생을 막고, 저장한 상태에 따라 재생을 복원한다.
        player.playWhenReady = false
        player.setMediaSources(mediaSources, targetIndex, targetPositionMs)
        player.prepare()

        if (wasPlaying) {
            player.play()
        }

        return PlaybackQueueSyncResult(
            selectedMediaId = musicTrackList.getOrNull(targetIndex)?.id,
            queueChanged = true
        )
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
        latestTrackList: List<MusicTrackEntity>
    ) {
        val latestTrackIds = latestTrackList.map { it.id }
        val queueIds = currentMediaIds.toMutableList()

        for (index in queueIds.lastIndex downTo 0) {
            if (queueIds[index] !in latestTrackIds) {
                player.removeMediaItem(index)
                queueIds.removeAt(index)
            }
        }

        latestTrackList.forEachIndexed { targetIndex, track ->
            if (track.id !in queueIds) {
                player.addMediaSource(
                    targetIndex,
                    track.toProgressiveMediaSource(context)
                )
                queueIds.add(targetIndex, track.id)
            }
        }
    }

    fun playMusic(
        musicTrackList: List<MusicTrackEntity>,
        id: String
    ): Boolean {
        // 요청한 트랙 Id가 현재 재생 가능한 음악 목록에 없으면 실패로 처리한다
        if (musicTrackList.none { it.id == id }) return false

        // 유효한 재생 요청일 때만 큐를 동기화한다.
        syncQueue(musicTrackList)

        // 큐 동기화 이후 실제 Player 큐에서 선택한 곡의 위치를 다시 찾는다.
        val targetIndex = player.findMediaItemIndexById(id)
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
