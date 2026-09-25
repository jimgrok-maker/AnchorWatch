package com.jimgrok.anchorwatch

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.osmdroid.config.Configuration

class AnchorWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCH,
                "Anchor watch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing watch while you are at anchor"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                "Anchor dragging alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fires when the boat leaves the swing circle"
                enableVibration(true)
                setBypassDnd(true)
            }
        )
    }

    companion object {
        const val CHANNEL_WATCH = "anchor_watch"
        const val CHANNEL_ALARM = "anchor_alarm"
    }
}
