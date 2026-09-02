package com.remotescreen.companion

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.webrtc.*

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "screen_share_active_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData != null) {
                    startForegroundCast(resultCode, resultData)
                }
            }
            ACTION_STOP -> stopSelfExecution()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCast(resultCode: Int, data: Intent) {
        createNotificationChannel()

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply { action = ACTION_STOP }
        val pStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Sharing Active")
            .setContentText("Screen stream is running.")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pStopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions())
        peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelfExecution()
            }
        })

        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", null)
        val videoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer?.initialize(surfaceTextureHelper, applicationContext, videoSource?.capturerObserver)
        videoCapturer?.startCapture(1080, 1920, 30)
    }

    private fun stopSelfExecution() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            surfaceTextureHelper?.dispose()
            mediaProjection?.stop()
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Share Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
