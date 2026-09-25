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
import com.jimgrok.anchorwatch.data.WatchState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapOverlays() }

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
            Configuration.getInstance().userAgentValue =
                "AnchorWatch/1.0.1 (${ctx.packageName})"
            Configuration.getInstance().load(
                ctx,
                ctx.getSharedPreferences("osmdroid", 0)
            )
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
            val follow = when {
                followBoat && boat != null -> boat
                anchor != null -> anchor
                boat != null -> boat
                else -> null
            }
            if (follow != null) {
                if (map.zoomLevelDouble < 15) {
                    map.controller.setZoom(17.0)
                }
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
