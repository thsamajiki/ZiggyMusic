package com.hero.ziggymusic.service

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MusicMediaControllerConnector(context: Context) {
    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    suspend fun connect(): MediaController {
        val currentController = controller
        if (currentController?.isConnected == true) {
            return currentController
        }

        val connectedController = getOrCreateControllerFuture().awaitController()
        controller = connectedController
        return connectedController
    }

    suspend fun withController(action: (MediaController) -> Unit) {
        val controller = connect()
        if (controller.isConnected) {
            action(controller)
        }
    }

    @Synchronized
    private fun getOrCreateControllerFuture(): ListenableFuture<MediaController> {
        controllerFuture?.let { return it }

        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, MusicService::class.java)
        )
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        return future
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

    fun release() {
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }
}
