package com.tpappcc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.webrtc.*

/**
 * Consent-based local capture foundation.
 * The app still needs an authorized signaling/session before frames leave the device.
 */
class WebRtcCapture(private val context: Context) {
    private var factory: PeerConnectionFactory? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var egl: EglBase? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    fun startScreen(resultCode: Int, data: Intent): VideoTrack {
        stop()
        ensureWebRtcInitialized(context)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        egl = EglBase.create()
        capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        })
        videoSource = factory!!.createVideoSource(false)
        surfaceHelper = SurfaceTextureHelper.create("TPaPCC-Capture", egl!!.eglBaseContext)
        capturer!!.initialize(
            surfaceHelper,
            context,
            videoSource!!.capturerObserver
        )
        capturer!!.startCapture(1280, 720, 15)
        videoTrack = factory!!.createVideoTrack("TPaPCC-screen", videoSource)
        return videoTrack!!
    }

    fun stop() {
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose()
        capturer = null
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        surfaceHelper?.dispose()
        surfaceHelper = null
        egl?.release()
        egl = null
        factory?.dispose()
        factory = null
    }

    private companion object {
        @Volatile private var webRtcInitialized = false

        @Synchronized
        private fun ensureWebRtcInitialized(context: Context) {
            if (webRtcInitialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
            )
            webRtcInitialized = true
        }
    }
}
