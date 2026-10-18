// Muse Cyrene modification notice: 2026-10-18; unofficial fork; see CYRENE_MODIFICATIONS.md (GPL-3.0-or-later).
package io.zer0.muse.ui.speech

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.zer0.muse.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** Keeps an already-started reading alive while navigating away. Never starts recording. */
class ReadingPlaybackService : Service() {
    private val tts: TtsManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.reading_notification_title), NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(7412, readingNotification())
        scope.launch {
            delay(1000)
            var previous = tts.playbackState.value.status
            while (isActive && tts.hasActiveReading()) {
                val status = tts.playbackState.value.status
                if (status != previous) {
                    manager.notify(7412, readingNotification())
                    previous = status
                }
                delay(500)
            }
            stopSelf()
        }
    }

    private fun readingNotification(): android.app.Notification {
        val paused = tts.playbackState.value.status == PlaybackStatus.Paused
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, ReadingPlaybackService::class.java).setAction(TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 0, Intent(this, ReadingPlaybackService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.reading_notification_title))
            .setContentText(getString(R.string.reading_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                getString(if (paused) R.string.reading_resume else R.string.reading_pause), toggle,
            )
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.reading_stop), stop)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TOGGLE) {
            if (tts.playbackState.value.status == PlaybackStatus.Paused) tts.resume() else tts.pause()
            getSystemService(NotificationManager::class.java).notify(7412, readingNotification())
        }
        if (intent?.action == STOP) {
            tts.stop()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "muse_reading_playback"
        const val STOP = "stop_reading"
        const val TOGGLE = "toggle_reading"
    }
}
