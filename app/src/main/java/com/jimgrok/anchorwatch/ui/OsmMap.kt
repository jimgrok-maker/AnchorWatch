package com.jimgrok.anchorwatch.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.jimgrok.anchorwatch.data.WatchState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmMap(
    state: WatchState,
    followBoat: Boolean,
    modifier: Modifier = Modifier
) {
    val holder = remember { MapOverlays() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 14.0
                maxZoomLevel = 21.0
                controller.setZoom(18.0)
                val start = state.boat ?: state.anchor
                if (start != null) {
                    controller.setCenter(GeoPoint(start.latitude, start.longitude))
                }
                holder.track = Polyline().apply {
                    outlinePaint.color = AndroidColor.parseColor("#7EC8C8")
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.isAntiAlias = true
                }
                holder.circle = Polygon().apply {
                    fillPaint.color = AndroidColor.parseColor("#33E8C36A")
                    outlinePaint.color = AndroidColor.parseColor("#E8C36A")
                    outlinePaint.strokeWidth = 4f
                }
                holder.anchor = Marker(this).apply {
                    title = "Anchor"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                holder.boat = Marker(this).apply {
                    title = "Boat"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                overlays.add(holder.circle)
                overlays.add(holder.track)
                overlays.add(holder.anchor)
                overlays.add(holder.boat)
                holder.map = this
            }
        },
        update = { map ->
            val anchor = state.anchor
            val boat = state.boat
            if (anchor != null) {
                holder.anchor?.position = GeoPoint(anchor.latitude, anchor.longitude)
                holder.anchor?.title = "Hook"
                holder.circle?.points = Polygon.pointsAsCircle(
                    GeoPoint(anchor.latitude, anchor.longitude),
                    state.radiusM
                )
            } else {
                holder.circle?.points = emptyList()
            }
            if (boat != null) {
                holder.boat?.position = GeoPoint(boat.latitude, boat.longitude)
            }
            holder.track?.setPoints(
                state.track.map { GeoPoint(it.latitude, it.longitude) }
            )
            if (state.alarming) {
                holder.circle?.outlinePaint?.color = AndroidColor.parseColor("#FF5A4A")
                holder.circle?.fillPaint?.color = AndroidColor.parseColor("#44FF5A4A")
            } else {
                holder.circle?.outlinePaint?.color = AndroidColor.parseColor("#E8C36A")
                holder.circle?.fillPaint?.color = AndroidColor.parseColor("#33E8C36A")
            }
            val follow = if (followBoat) boat else anchor
            if (follow != null && map.zoomLevelDouble >= 14) {
                map.controller.setCenter(GeoPoint(follow.latitude, follow.longitude))
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
