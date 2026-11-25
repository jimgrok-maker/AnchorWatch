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
    private var pendingPlayer: MediaPlayer? = null
    private var warnPlayer: MediaPlayer? = null
    private var sessionId = 0

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun start() {
        if (player != null || pendingPlayer != null) return
        val mySession = ++sessionId
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            Log.w(TAG, "No default alarm/notify ringtone URI; vibration only")
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
            setOnPreparedListener { prepared ->
                if (mySession != sessionId) {
                    prepared.releaseQuietly()
                    return@setOnPreparedListener
                }
                if (pendingPlayer === prepared) pendingPlayer = null
                try {
                    prepared.start()
                    player = prepared
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaPlayer.start() failed in onPrepared", e)
                    prepared.releaseQuietly()
                }
            }
            setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "MediaPlayer onError what=$what extra=$extra")
                if (pendingPlayer === failed) pendingPlayer = null
                if (player === failed) player = null
                failed.releaseQuietly()
                true
            }
        }
        pendingPlayer = mp
        try {
            mp.setDataSource(context, uri)
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "setDataSource/prepareAsync failed; falling back to vibration", e)
            if (pendingPlayer === mp) pendingPlayer = null
            mp.releaseQuietly()
        }
        vibrate()
    }

    /** One-shot warning for a lost GPS fix. Does not start the looping drag siren. */
    fun warnOnce() {
        val pattern = longArrayOf(0, 400, 200, 400)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (uri == null) return
        warnPlayer?.releaseQuietly()
        warnPlayer = null
        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                setOnPreparedListener { prepared ->
                    if (warnPlayer !== prepared) {
                        prepared.releaseQuietly()
                    } else {
                        runCatching { prepared.start() }
                            .onFailure {
                                warnPlayer = null
                                prepared.releaseQuietly()
                            }
                    }
                }
                setOnCompletionListener { finished ->
                    if (warnPlayer === finished) warnPlayer = null
                    finished.releaseQuietly()
                }
                setOnErrorListener { failed, _, _ ->
                    if (warnPlayer === failed) warnPlayer = null
                    failed.releaseQuietly()
                    true
                }
            }
            warnPlayer = mp
            mp.setDataSource(context, uri)
            mp.prepareAsync()
        }.onFailure {
            Log.w(TAG, "warnOnce failed", it)
            warnPlayer?.releaseQuietly()
            warnPlayer = null
        }
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
        sessionId++
        pendingPlayer?.releaseQuietly()
        pendingPlayer = null
        player?.releaseQuietly()
        player = null
        warnPlayer?.releaseQuietly()
        warnPlayer = null
        vibrator?.cancel()
    }

    companion object {
        private const val TAG = "AlarmPlayer"
    }
}
