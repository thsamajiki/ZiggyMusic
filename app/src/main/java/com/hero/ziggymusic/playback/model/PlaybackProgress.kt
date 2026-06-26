package com.hero.ziggymusic.playback.model

import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlin.time.Duration.Companion.milliseconds

/**
 * 미디어의 전체 재생 시간과 현재 재생 위치를 나타내는 불변 스냅샷이다.
 *
 * Player가 아직 재생 정보를 제공하지 않는 경우 각 값은 0으로 보정될 수 있다.
 *
 * @property durationMs 미디어의 전체 재생 시간(밀리초)
 * @property positionMs 현재 재생 위치(밀리초)
 */
data class PlaybackProgress(
    val durationMs: Long,
    val positionMs: Long,
)

/**
 * Player의 현재 재생 진행률을 조회한다.
 *
 * 전체 재생 시간이나 현재 위치가 음수이면 0으로 보정한다.
 *
 * @receiver 진행률을 조회할 Player
 * @return 보정된 전체 재생 시간과 현재 위치
 */
fun Player.currentPlaybackProgress(): PlaybackProgress =
    PlaybackProgress(
        durationMs = duration.coerceAtLeast(0L),
        positionMs = currentPosition.coerceAtLeast(0L),
    )

/**
 * Player의 재생 진행률 변경을 스트림으로 제공한다.
 *
 * 재생 중이고 주기 갱신이 활성화된 동안 현재 위치를 반복해서 방출한다.
 * 미디어 아이템이 전환되면 새 트랙의 진행률도 별도로 방출한다.
 *
 * @receiver 진행률을 관찰할 Player
 * @param updatesEnabled 주기적인 진행률 갱신의 활성화 여부를 제공하는 스트림
 * @return 재생 진행률을 방출하는 cold Flow
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Player.playbackProgressUpdates(
    updatesEnabled: Flow<Boolean>,
): Flow<PlaybackProgress> =
    merge(
        periodicProgressUpdates(updatesEnabled),
        mediaTransitionProgressUpdates(),
    )

/** 재생 중이고 주기 갱신이 허용된 동안 진행률을 반복 방출.
 * 갱신이 비활성화되면 현재 진행률을 마지막으로 한 번 방출.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Player.periodicProgressUpdates(
    updatesEnabled: Flow<Boolean>,
): Flow<PlaybackProgress> =
    combine(
        playingStateUpdates(),
        updatesEnabled.distinctUntilChanged(),
    ) { isPlaying, isEnabled ->
        isPlaying && isEnabled
    }
        .distinctUntilChanged()
        .flatMapLatest { shouldUpdate ->
            if (shouldUpdate) {
                progressTicker()
            } else {
                flowOf(currentPlaybackProgress())
            }
        }

/** 트랙 전환 후 Player의 위치 정보가 갱신될 시간을 기다린 뒤
 * 새 트랙의 진행률을 방출.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Player.mediaTransitionProgressUpdates(): Flow<PlaybackProgress> =
    mediaItemTransitionSignals()
        .mapLatest {
            // 트랙 전환 직후 Media3의 duration과 position이 안정될 시간을 기다린다.
            delay(MEDIA_TRANSITION_PROGRESS_REFRESH_DELAY_MS.milliseconds)
            currentPlaybackProgress()
        }

/* 미디어 아이템이 전환될 때마다 갱신 신호를 방출. */
private fun Player.mediaItemTransitionSignals(): Flow<Unit> =
    callbackFlow {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                trySend(Unit)
            }
        }

        addListener(listener)

        awaitClose {
            removeListener(listener)
        }
    }

/* 현재 재생 여부를 즉시 방출하고, 이후 재생 상태가 변경될 때마다 새 값을 방출. */
private fun Player.playingStateUpdates(): Flow<Boolean> =
    callbackFlow {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trySend(isPlaying)
            }
        }

        addListener(listener)
        trySend(isPlaying)

        awaitClose {
            removeListener(listener)
        }
    }.distinctUntilChanged()

/**
 * 현재 진행률을 즉시 방출하고, 이후 갱신 시점을 재생 위치에 맞춰 조정한다.
 *
 * 초 경계를 바로 앞두고 갱신된 경우 250ms 전체를 기다리지 않고
 * 짧은 지연 후 다시 확인해 시간 표시가 늦어지는 것을 방지한다.
 */
private fun Player.progressTicker(): Flow<PlaybackProgress> =
    flow {
        while (true) {
            val progress = currentPlaybackProgress()
            emit(progress)

            delay(
                calculateProgressUpdateDelay(
                    positionMs = progress.positionMs,
                ).milliseconds
            )
        }
    }

/**
 * 현재 재생 위치를 기준으로 다음 250ms 경계 직후까지의 지연 시간을 계산한다.
 */
private fun calculateProgressUpdateDelay(positionMs: Long): Long =
    (
            PROGRESS_UPDATE_INTERVAL_MS -
                    positionMs % PROGRESS_UPDATE_INTERVAL_MS +
                    PROGRESS_UPDATE_BOUNDARY_OFFSET_MS
            ).coerceIn(
            MIN_PROGRESS_UPDATE_DELAY_MS,
            MAX_PROGRESS_UPDATE_DELAY_MS,
        )

private const val PROGRESS_UPDATE_INTERVAL_MS = 250L // 초 단위 표시 경계를 계산하기 위한 기준 간격
private const val PROGRESS_UPDATE_BOUNDARY_OFFSET_MS = 30L // 초 경계가 지난 직후 UI가 갱신되도록 추가하는 보정값
private const val MEDIA_TRANSITION_PROGRESS_REFRESH_DELAY_MS = 100L // 트랙 전환 후 Player의 위치 정보가 갱신되기를 기다리는 시간

// 계산된 갱신 지연 시간의 최소·최대 허용값
private const val MIN_PROGRESS_UPDATE_DELAY_MS = 50L // ticker의 계산 결과가 너무 짧지 않게
private const val MAX_PROGRESS_UPDATE_DELAY_MS = 280L // ticker의 계산 결과가 너무 길지 않게
