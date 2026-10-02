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

    fun startScreen(resultCode: Int, data: Intent): VideoTrack {
        stop()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        })
        videoSource = factory!!.createVideoSource(false)
        capturer!!.initialize(
            SurfaceTextureHelper.create("TPaPCC-Capture", EglBase.create().eglBaseContext),
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
        factory?.dispose()
        factory = null
    }
}
