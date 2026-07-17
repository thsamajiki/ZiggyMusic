package com.hero.ziggymusic.playback.service

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MusicMediaControllerConnector(context: Context) {
    private val appContext = context.applicationContext

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    /**
     * 이미 연결된 MediaController가 있으면 재사용한다.
     *
     * 연결에 성공하면 MediaController를 반환하고,
     * 코루틴 취소를 제외한 연결 실패는 기록한 뒤 null을 반환한다.
     *
     * CancellationException은 구조화된 동시성과 생명주기 취소를
     * 유지하기 위해 호출자에게 다시 전달한다.
     */
    suspend fun connect(): MediaController? {
        val currentController = controller

        if (currentController?.isConnected == true) {
            return currentController
        }

        var connectionFuture: ListenableFuture<MediaController>? = null

        return try {
            val future = getOrCreateControllerFuture()
            connectionFuture = future

            val connectedController = future.awaitController()

            if (!connectedController.isConnected) {
                invalidateConnection(future)
                null
            } else {
                controller = connectedController
                connectedController
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MediaController 연결 실패", e)

            connectionFuture?.let { failedFuture ->
                invalidateConnection(failedFuture)
            } ?: invalidateConnection()

            null
        }
    }

    /**
     * 연결된 MediaController를 사용해 명령을 실행한다.
     *
     * 명령이 정상적으로 실행되면 true를 반환한다.
     * 코루틴 취소를 제외한 연결 또는 명령 실행 실패는
     * 내부에서 기록한 뒤 false를 반환한다.
     */
    suspend fun withController(
        action: (MediaController) -> Unit
    ): Boolean {
        return try {
            val connectedController = connect() ?: return false

            if (!connectedController.isConnected) {
                invalidateConnection()
                false
            } else {
                action(connectedController)
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "MediaController 명령 실행 실패", e)

            invalidateConnection()
            false
        }
    }

    @Synchronized
    private fun getOrCreateControllerFuture(): ListenableFuture<MediaController> {
        controllerFuture?.let { return it }

        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, MusicService::class.java)
        )

        return MediaController.Builder(appContext, sessionToken)
            .buildAsync()
            .also { future ->
                controllerFuture = future
            }
    }

    private suspend fun ListenableFuture<MediaController>.awaitController(): MediaController {
        return suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    try {
                        if (continuation.isActive) {
                            continuation.resume(Futures.getDone(this))
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                },
                ContextCompat.getMainExecutor(appContext)
            )
        }
    }

    /**
     * 전달된 Future가 현재 Future인 경우에만 폐기한다.
     * 이전 연결의 늦은 실패가 새 연결을 제거하는 상황을 방지한다.
     */
    @Synchronized
    private fun invalidateConnection(
        expectedFuture: ListenableFuture<MediaController>? = null
    ) {
        if (expectedFuture != null && controllerFuture !== expectedFuture) {
            return
        }

        controller = null

        controllerFuture?.let { future ->
            runCatching {
                MediaController.releaseFuture(future)
            }.onFailure { error ->
                Log.w(TAG, "MediaController Future 해제 실패", error)
            }
        }

        controllerFuture = null
    }

    fun release() {
        invalidateConnection()
    }

    companion object {
        private const val TAG = "MediaControllerConnector"
    }
}
