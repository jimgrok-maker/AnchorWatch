package com.jimgrok.anchorwatch.service

import com.jimgrok.anchorwatch.data.WatchState

/** Which watch notification is shown. */
internal enum class NotificationKind { DRAGGING, GPS_LOST_ALARM, GPS_LOST, WAITING, SILENCED, WATCHING }

/**
 * Everything the watch notification displays. Equal models render identically, so the service
 * only calls NotificationManager.notify when the model changes (not on every 1 s tick or fix).
 * [distance] and [radius] are blank for kinds that do not show them.
 */
internal data class NotificationModel(
    val kind: NotificationKind,
    val distance: String = "",
    val radius: String = "",
)

internal fun notificationModel(state: WatchState): NotificationModel {
    val distance = if (state.useFeet) "${state.distanceFt.toInt()} ft" else "${state.distanceM.toInt()} m"
    val radius = if (state.useFeet) "${state.radiusFt} ft" else "${state.radiusM.toInt()} m"
    return when {
        state.alarming && state.gpsLost -> NotificationModel(NotificationKind.GPS_LOST_ALARM)
        state.alarming -> NotificationModel(NotificationKind.DRAGGING, distance, radius)
        state.gpsLost -> NotificationModel(NotificationKind.GPS_LOST)
        state.waitingForHook -> NotificationModel(NotificationKind.WAITING)
        state.silenced -> NotificationModel(NotificationKind.SILENCED, distance, radius)
        else -> NotificationModel(NotificationKind.WATCHING, distance, radius)
    }
}
