package com.jimgrok.anchorwatch.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jimgrok.anchorwatch.data.WatchStore
import com.jimgrok.anchorwatch.data.WatchState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

// Pre-parsed once; re-parsing hex strings on every per-second recomposition is wasteful.
private val COL_TRACK = AndroidColor.parseColor("#7EC8C8")
private val COL_CIRCLE_OUT = AndroidColor.parseColor("#E8C36A")
private val COL_CIRCLE_FILL = AndroidColor.parseColor("#33E8C36A")
private val COL_ALARM_OUT = AndroidColor.parseColor("#FF5A4A")
private val COL_ALARM_FILL = AndroidColor.parseColor("#44FF5A4A")

@Composable
fun OsmMap(
    state: WatchState,
    followBoat: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapOverlays() }
    var lastTrackVersion by remember { androidx.compose.runtime.mutableLongStateOf(-1L) }
    var lastFollowKey by remember { mutableStateOf<String?>(null) }
    var lastAlarming by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.map?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.map?.onPause()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // OSMDroid config is already loaded once in Application.onCreate; do not reload
            // here (the previous reload also overwrote the user agent with a stale version).
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                isTilesScaledToDpi = true
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(39.0, -84.5))
                holder.track = Polyline().apply {
                    outlinePaint.color = COL_TRACK
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.isAntiAlias = true
                }
                holder.circle = Polygon().apply {
                    fillPaint.color = COL_CIRCLE_FILL
                    outlinePaint.color = COL_CIRCLE_OUT
                    outlinePaint.strokeWidth = 4f
                }
                holder.anchor = Marker(this).apply {
                    title = "Hook"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    isEnabled = false
                }
                holder.boat = Marker(this).apply {
                    title = "Boat"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isEnabled = false
                }
                overlays.add(holder.circle)
                overlays.add(holder.track)
                overlays.add(holder.anchor)
                overlays.add(holder.boat)
                holder.map = this
                onResume()
            }
        },
        update = { map ->
            val anchor = state.anchor
            val boat = state.boat
            if (anchor != null) {
                holder.anchor?.position = GeoPoint(anchor.latitude, anchor.longitude)
                holder.anchor?.isEnabled = true
                holder.circle?.points = Polygon.pointsAsCircle(
                    GeoPoint(anchor.latitude, anchor.longitude),
                    state.radiusM
                )
            } else {
                holder.anchor?.isEnabled = false
                holder.circle?.points = emptyList()
            }
            if (boat != null) {
                holder.boat?.position = GeoPoint(boat.latitude, boat.longitude)
                holder.boat?.isEnabled = true
            } else {
                holder.boat?.isEnabled = false
            }

            // Rebuild the polyline only when the track actually grew (new fix), not every
            // recomposition. WatchStore.trackVersion bumps on every real track change.
            val tv = WatchStore.trackVersionLong
            if (tv != lastTrackVersion) {
                lastTrackVersion = tv
                holder.track?.setPoints(WatchStore.trackPoints().map { GeoPoint(it.latitude, it.longitude) })
            }

            // Only touch the circle paints when the alarm state flips.
            if (state.alarming != lastAlarming) {
                lastAlarming = state.alarming
                if (state.alarming) {
                    holder.circle?.outlinePaint?.color = COL_ALARM_OUT
                    holder.circle?.fillPaint?.color = COL_ALARM_FILL
                } else {
                    holder.circle?.outlinePaint?.color = COL_CIRCLE_OUT
                    holder.circle?.fillPaint?.color = COL_CIRCLE_FILL
                }
            }

            // Re-center only when the follow target actually changed (or first time), not
            // every per-second recomposition while the boat is stationary.
            val follow = when {
                followBoat && boat != null -> boat
                anchor != null -> anchor
                boat != null -> boat
                else -> null
            }
            if (follow != null) {
                val key = "${follow.latitude},${follow.longitude},${followBoat}"
                if (key != lastFollowKey) {
                    lastFollowKey = key
                    if (map.zoomLevelDouble < 15) {
                        map.controller.setZoom(17.0)
                    }
                    map.controller.setCenter(GeoPoint(follow.latitude, follow.longitude))
                }
            }
            map.invalidate()
        }
    )
}

private class MapOverlays {
    var map: MapView? = null
    var track: Polyline? = null
    var circle: Polygon? = null
    var anchor: Marker? = null
    var boat: Marker? = null
}
