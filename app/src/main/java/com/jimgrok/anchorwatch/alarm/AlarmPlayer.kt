package com.jimgrok.anchorwatch.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var preparing = false
    private var dismissed = false
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun start() {
        if (player != null || preparing) return
        dismissed = false
        preparing = true
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            Log.w(TAG, "No default alarm/notify ringtone URI; vibration only")
            preparing = false
            vibrate()
            return
        }
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            setVolume(1f, 1f)
            setOnPreparedListener {
                preparing = false
                if (dismissed) {
                    releaseQuietly()
                    return@setOnPreparedListener
                }
                try {
                    start()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaPlayer.start() failed in onPrepared", e)
                    releaseQuietly()
                    return@setOnPreparedListener
                }
                this@AlarmPlayer.player = this
            }
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer onError what=$what extra=$extra")
                preparing = false
                releaseQuietly()
                true
            }
        }
        try {
            mp.setDataSource(context, uri)
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource/prepareAsync failed; falling back to vibration", e)
            preparing = false
            mp.release()
        }
        vibrate()
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 600, 250, 600, 250, 900)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun MediaPlayer.releaseQuietly() {
        try {
            if (isPlaying) stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "MediaPlayer.stop() in releaseQuietly", e)
        }
        try {
            release()
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer.release() in releaseQuietly", e)
        }
    }

    fun stop() {
        dismissed = true
        preparing = false
        player?.let { p ->
            try {
                if (p.isPlaying) p.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "MediaPlayer.stop() in stop()", e)
            }
            try {
                p.release()
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer.release() in stop()", e)
            }
        }
        player = null
        vibrator?.cancel()
    }

    companion object {
        private const val TAG = "AlarmPlayer"
    }
}
