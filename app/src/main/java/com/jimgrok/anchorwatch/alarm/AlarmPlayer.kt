package com.jimgrok.anchorwatch.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.jimgrok.anchorwatch.R

class AlarmPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var preparing = false
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun start() {
        if (player != null || preparing) return
        preparing = true
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
                // If the alarm was dismissed while we were still preparing, bail out.
                if (this@AlarmPlayer.player != null) return@setOnPreparedListener
                try {
                    start()
                } catch (_: IllegalStateException) {
                    releaseQuietly()
                    return@setOnPreparedListener
                }
                this@AlarmPlayer.player = this
            }
            setOnErrorListener { _, _, _ ->
                preparing = false
                // Default ringtone is null/unusable — fall back to the bundled alarm so
                // the watch is never silent.
                if (!hasDataSource) {
                    releaseQuietly()
                    start()
                } else {
                    releaseQuietly()
                }
                true
            }
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            if (uri != null) {
                mp.setDataSource(context, uri)
            } else {
                setFallbackDataSource(mp)
            }
            // prepareAsync() so the (potentially blocking) ringtone decode never stalls the
            // main-thread location callback that fires the alarm.
            mp.prepareAsync()
        } catch (_: Exception) {
            setFallbackDataSource(mp)
            try {
                mp.prepareAsync()
            } catch (_: Exception) {
                preparing = false
                mp.release()
                player = null
            }
        }
        val pattern = longArrayOf(0, 600, 250, 600, 250, 900)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun setFallbackDataSource(mp: MediaPlayer) {
        // A bundled alarm guarantees the watch can always make noise, even on a device whose
        // default ringtone is null or set to a silent tone.
        mp.setDataSource(context, context.resources.openRawResourceFd(R.raw.alarm_fallback))
    }

    private fun MediaPlayer.releaseQuietly() {
        try {
            if (isPlaying) stop()
        } catch (_: IllegalStateException) {
        }
        try {
            release()
        } catch (_: Exception) {
        }
    }

    fun stop() {
        preparing = false
        player?.let { p ->
            try {
                if (p.isPlaying) p.stop()
            } catch (_: IllegalStateException) {
            }
            try {
                p.release()
            } catch (_: Exception) {
            }
        }
        player = null
        vibrator?.cancel()
    }
}
