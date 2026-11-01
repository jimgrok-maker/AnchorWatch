package com.jimgrok.anchorwatch.service

import com.jimgrok.anchorwatch.data.GeoFix
import com.jimgrok.anchorwatch.data.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationModelTest {
    private val fix = GeoFix(41.5, -81.7, 4f, 1L)
    private val watching = WatchState(watching = true, anchor = fix, boat = fix, radiusFt = 100, distanceFt = 42.7)

    @Test fun unchangedDisplayedContentIsEqualEvenIfOtherFieldsTick() {
        val later = watching.copy(
            lastUpdateMs = 99L,
            lastAccurateFixMs = 99L,
            boat = fix.copy(latitude = 41.50001, timeMs = 99L),
            distanceFt = 42.9, // same whole feet
        )
        assertEquals(notificationModel(watching), notificationModel(later))
    }

    @Test fun wholeFootChangeIsADifferentModel() {
        assertNotEquals(notificationModel(watching), notificationModel(watching.copy(distanceFt = 43.1)))
    }

    @Test fun unitSwitchChangesModel() {
        assertNotEquals(notificationModel(watching), notificationModel(watching.copy(useFeet = false)))
    }

    @Test fun kindFollowsState() {
        assertEquals(NotificationKind.WATCHING, notificationModel(watching).kind)
        assertEquals(NotificationKind.WAITING, notificationModel(watching.copy(anchor = null)).kind)
        assertEquals(NotificationKind.SILENCED, notificationModel(watching.copy(silenced = true)).kind)
        assertEquals(NotificationKind.GPS_LOST, notificationModel(watching.copy(gpsLost = true)).kind)
        assertEquals(NotificationKind.DRAGGING, notificationModel(watching.copy(alarming = true)).kind)
        assertEquals(
            NotificationKind.GPS_LOST_ALARM,
            notificationModel(watching.copy(alarming = true, gpsLost = true)).kind,
        )
    }

    @Test fun kindsWithoutDistanceIgnoreDistanceChanges() {
        val lost = watching.copy(gpsLost = true)
        assertEquals(notificationModel(lost), notificationModel(lost.copy(distanceFt = 80.0)))
        val waiting = watching.copy(anchor = null)
        assertEquals(notificationModel(waiting), notificationModel(waiting.copy(distanceFt = 80.0)))
    }

    @Test fun alarmingTakesPriorityOverSilenced() {
        assertEquals(
            NotificationKind.DRAGGING,
            notificationModel(watching.copy(alarming = true, silenced = true)).kind,
        )
    }
}
