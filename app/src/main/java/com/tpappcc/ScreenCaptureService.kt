package com.tpappcc

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*

class ScreenCaptureService : Service() {
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        try {
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

            // MainActivity waits for this callback before ScreenCapturerAndroid
            // consumes the MediaProjection consent token.
            val ready = if (Build.VERSION.SDK_INT >= 33) {
                i?.getParcelableExtra("ready", ResultReceiver::class.java)
            } else {
                @Suppress("DEPRECATION")
                i?.getParcelableExtra("ready")
            }
            ready?.send(1, Bundle())
        } catch (e: Exception) {
            val ready = if (Build.VERSION.SDK_INT >= 33) {
                i?.getParcelableExtra("ready", ResultReceiver::class.java)
            } else {
                @Suppress("DEPRECATION")
                i?.getParcelableExtra("ready")
            }
            ready?.send(0, Bundle().apply { putString("error", e.message) })
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
