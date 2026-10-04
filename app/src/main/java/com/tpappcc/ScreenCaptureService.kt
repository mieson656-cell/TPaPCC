package com.tpappcc

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*

class ScreenCaptureService : Service() {
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, "tpaPcc")
            .setContentTitle("TPaPCC")
            .setContentText("Трансляция экрана активна")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(20, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(20, notification)
        }

        // The MediaProjection consent token is consumed by WebRtcSession/ScreenCapturerAndroid.
        // This service intentionally does not call getMediaProjection() itself, which avoids
        // consuming the same one-time consent token twice on newer Android versions.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
