package com.tpappcc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.webrtc.*

class WebRtcSession(
    private val context: Context,
    private val sessionId: String,
    private val onStatus: (String) -> Unit
) {
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var egl: EglBase? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var lastSignalId = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var remoteDescriptionSet = false
    private val pendingIce = mutableListOf<IceCandidate>()

    fun startScreen(data: Intent) {
        if (running) return
        running = true
        onStatus("Создание WebRTC-сессии…")

        ensureWebRtcInitialized(context)
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        egl = EglBase.create()

        val servers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        peer = factory!!.createPeerConnection(PeerConnection.RTCConfiguration(servers), observer)
        if (peer == null) {
            onStatus("WebRTC: не удалось создать PeerConnection")
            stop()
            return
        }

        videoSource = factory!!.createVideoSource(false)
        capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        })
        surfaceHelper = SurfaceTextureHelper.create("TPaPCC-Screen", egl!!.eglBaseContext)
        capturer!!.initialize(surfaceHelper, context, videoSource!!.capturerObserver)
        capturer!!.startCapture(1280, 720, 15)

        videoTrack = factory!!.createVideoTrack("TPaPCC-screen", videoSource)
        peer!!.addTrack(videoTrack, listOf("TPaPCC"))
        peer!!.createOffer(sdpObserver, MediaConstraints())
        poll()
    }

    private val sdpObserver = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {
            peer?.setLocalDescription(this, desc)
            val payload = JSONObject()
                .put("type", desc.type.canonicalForm())
                .put("sdp", desc.description)
            TpaPccApi.sendSignal(context, sessionId, "offer", payload) { ok, _ ->
                onStatus(if (ok) "Ожидаем ответ WebRTC…" else "Не удалось отправить offer")
            }
        }

        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String) {
            onStatus("WebRTC offer: $error")
            stop()
        }
        override fun onSetFailure(error: String) {
            onStatus("WebRTC SDP: $error")
            stop()
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            val p = JSONObject()
                .put("sdpMid", c.sdpMid)
                .put("sdpMLineIndex", c.sdpMLineIndex)
                .put("candidate", c.sdp)
            TpaPccApi.sendSignal(context, sessionId, "ice", p) { _, _ -> }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            onStatus("WebRTC: $state")
            if (state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.CLOSED
            ) {
                stop()
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {}
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            onStatus("WebRTC: $newState")
            if (newState == PeerConnection.PeerConnectionState.FAILED ||
                newState == PeerConnection.PeerConnectionState.CLOSED
            ) {
                stop()
            }
        }
    }

    private fun poll() {
        if (!running) return
        TpaPccApi.pollSignals(context, sessionId, lastSignalId) { ok, messages ->
            if (!running) return@pollSignals
            if (ok && messages != null) {
                for (i in 0 until messages.length()) {
                    val m = messages.optJSONObject(i) ?: continue
                    lastSignalId = maxOf(lastSignalId, m.optLong("id", lastSignalId))
                    when (m.optString("message_type")) {
                        "answer" -> {
                            val p = m.optJSONObject("payload") ?: continue
                            val type = SessionDescription.Type.fromCanonicalForm(
                                p.optString("type", "answer")
                            )
                            val sdp = p.optString("sdp")
                            if (sdp.isNotBlank()) {
                                peer?.setRemoteDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        remoteDescriptionSet = true
                                        val queued = pendingIce.toList()
                                        pendingIce.clear()
                                        queued.forEach { peer?.addIceCandidate(it) }
                                    }

                                    override fun onSetFailure(error: String) {
                                        onStatus("WebRTC remote SDP: $error")
                                        stop()
                                    }

                                    override fun onCreateSuccess(desc: SessionDescription) {}
                                    override fun onCreateFailure(error: String) {}
                                }, SessionDescription(type, sdp))
                            }
                        }

                        "ice" -> {
                            val p = m.optJSONObject("payload") ?: continue
                            val mid = if (p.isNull("sdpMid")) null else p.optString("sdpMid")
                            val idx = p.optInt("sdpMLineIndex", 0)
                            val candidate = p.optString("candidate")
                            if (candidate.isNotBlank()) {
                                val ice = IceCandidate(mid, idx, candidate)
                                if (remoteDescriptionSet) {
                                    peer?.addIceCandidate(ice)
                                } else {
                                    pendingIce.add(ice)
                                }
                            }
                        }

                        "control" -> {\n                            val p = m.optJSONObject("payload") ?: continue\n                            onControl(p)\n                        }\n\n                        "bye" -> stop()
                    }
                }
            }
            if (running) handler.postDelayed({ poll() }, 700)
        }
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

    fun stop() {
        if (!running) return
        running = false
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose()
        capturer = null
        surfaceHelper?.dispose()
        surfaceHelper = null
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        peer?.close()
        peer = null
        factory?.dispose()
        factory = null
        egl?.release()
        egl = null
        remoteDescriptionSet = false
        pendingIce.clear()
        handler.removeCallbacksAndMessages(null)
        onStatus("WebRTC остановлен")
    }
}
