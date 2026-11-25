package com.jimgrok.anchorwatch.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jimgrok.anchorwatch.data.WatchState
import com.jimgrok.anchorwatch.data.WatchStore
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

// Pre-parsed once; avoids per-second hex parsing during recomposition.
private val COL_TRACK = AndroidColor.parseColor("#38BDF8")
private val COL_CIRCLE_OUT = AndroidColor.parseColor("#F59E0B")
private val COL_CIRCLE_FILL = AndroidColor.parseColor("#26F59E0B")
private val COL_ALARM_OUT = AndroidColor.parseColor("#F87171")
private val COL_ALARM_FILL = AndroidColor.parseColor("#3DF87171")
private val COL_ACC_OUT = AndroidColor.parseColor("#6638BDF8")
private val COL_ACC_FILL = AndroidColor.parseColor("#1838BDF8")

@Composable
fun OsmMap(
    state: WatchState,
    followBoat: Boolean,
    recenterToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { MapOverlays() }
    var lastTrackVersion by remember { mutableLongStateOf(-1L) }
    var lastFollowKey by remember { mutableStateOf<String?>(null) }
    var lastCircleKey by remember { mutableStateOf<String?>(null) }
    var lastAccKey by remember { mutableStateOf<String?>(null) }
    var lastAlarming by remember { mutableStateOf<Boolean?>(null) }

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
            // OSMDroid config is already loaded once in Application.onCreate; do not reload here.
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                isTilesScaledToDpi = true
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(39.0, -84.5))

                holder.anchorIcon = buildAnchorDrawable(ctx)
                holder.boatOkIcon = buildBoatDrawable(ctx, AndroidColor.parseColor("#34D399"))
                holder.boatAlarmIcon = buildBoatDrawable(ctx, AndroidColor.parseColor("#F87171"))

                holder.circle = Polygon().apply {
                    fillPaint.color = COL_CIRCLE_FILL
                    outlinePaint.color = COL_CIRCLE_OUT
                    outlinePaint.strokeWidth = 5f
                    outlinePaint.isAntiAlias = true
                }
                holder.accuracyCircle = Polygon().apply {
                    fillPaint.color = COL_ACC_FILL
                    outlinePaint.color = COL_ACC_OUT
                    outlinePaint.strokeWidth = 2.5f
                    outlinePaint.isAntiAlias = true
                }
                holder.track = Polyline().apply {
                    outlinePaint.color = COL_TRACK
                    outlinePaint.strokeWidth = 7f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.isAntiAlias = true
                }
                holder.anchor = Marker(this).apply {
                    title = "Hook"
                    icon = holder.anchorIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isEnabled = false
                }
                holder.boat = Marker(this).apply {
                    title = "Boat"
                    icon = holder.boatOkIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    isEnabled = false
                }
                overlays.add(holder.circle)
                overlays.add(holder.accuracyCircle)
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

            val circleKey = if (anchor != null) {
                "${anchor.latitude},${anchor.longitude},${state.radiusFt}"
            } else {
                "none"
            }
            if (circleKey != lastCircleKey) {
                lastCircleKey = circleKey
                if (anchor != null) {
                    val pt = GeoPoint(anchor.latitude, anchor.longitude)
                    holder.anchor?.position = pt
                    holder.anchor?.isEnabled = true
                    holder.circle?.points = Polygon.pointsAsCircle(pt, state.radiusM)
                } else {
                    holder.anchor?.isEnabled = false
                    holder.circle?.points = emptyList()
                }
            }

            if (boat != null) {
                val boatPt = GeoPoint(boat.latitude, boat.longitude)
                holder.boat?.position = boatPt
                holder.boat?.isEnabled = true
                val accKey = "${boat.latitude},${boat.longitude},${boat.accuracyM.toInt()}"
                if (accKey != lastAccKey) {
                    lastAccKey = accKey
                    if (boat.accuracyM > 1f && boat.accuracyM <= 150f) {
                        holder.accuracyCircle?.points = Polygon.pointsAsCircle(boatPt, boat.accuracyM.toDouble())
                    } else {
                        holder.accuracyCircle?.points = emptyList()
                    }
                }
            } else {
                holder.boat?.isEnabled = false
                if (lastAccKey != "none") {
                    lastAccKey = "none"
                    holder.accuracyCircle?.points = emptyList()
                }
            }

            val tv = WatchStore.trackVersionLong
            if (tv != lastTrackVersion) {
                lastTrackVersion = tv
                holder.track?.setPoints(WatchStore.trackPoints().map { GeoPoint(it.latitude, it.longitude) })
            }

            if (state.alarming != lastAlarming) {
                lastAlarming = state.alarming
                if (state.alarming) {
                    holder.circle?.outlinePaint?.color = COL_ALARM_OUT
                    holder.circle?.fillPaint?.color = COL_ALARM_FILL
                    holder.boat?.icon = holder.boatAlarmIcon
                } else {
                    holder.circle?.outlinePaint?.color = COL_CIRCLE_OUT
                    holder.circle?.fillPaint?.color = COL_CIRCLE_FILL
                    holder.boat?.icon = holder.boatOkIcon
                }
            }

            val follow = when {
                followBoat && boat != null -> boat
                anchor != null -> anchor
                boat != null -> boat
                else -> null
            }
            if (follow != null) {
                val key = "${follow.latitude},${follow.longitude},$followBoat,$recenterToken"
                if (key != lastFollowKey) {
                    lastFollowKey = key
                    if (map.zoomLevelDouble < 15.5) {
                        map.controller.setZoom(18.0)
                    }
                    map.controller.setCenter(GeoPoint(follow.latitude, follow.longitude))
                }
            }
            map.invalidate()
        }
    )
}

private fun buildAnchorDrawable(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt().coerceAtLeast(28)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val r = size * 0.34f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#0E1015")
        style = Paint.Style.FILL
    }
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#F59E0B")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#F59E0B")
        style = Paint.Style.FILL
    }

    c.drawCircle(cx, cy, r + 2f * density, bgPaint)
    c.drawCircle(cx, cy, r, ringPaint)
    c.drawLine(cx - r * 1.25f, cy, cx - r * 0.5f, cy, crossPaint)
    c.drawLine(cx + r * 0.5f, cy, cx + r * 1.25f, cy, crossPaint)
    c.drawLine(cx, cy - r * 1.25f, cx, cy - r * 0.5f, crossPaint)
    c.drawLine(cx, cy + r * 0.5f, cx, cy + r * 1.25f, crossPaint)
    c.drawCircle(cx, cy, 2.5f * density, centerPaint)
    return BitmapDrawable(context.resources, bmp)
}

private fun buildBoatDrawable(context: Context, fillColor: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (24 * density).toInt().coerceAtLeast(24)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        alpha = 68
        style = Paint.Style.FILL
    }
    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    c.drawCircle(cx, cy, size * 0.46f, haloPaint)
    c.drawCircle(cx, cy, size * 0.30f, rimPaint)
    c.drawCircle(cx, cy, size * 0.22f, corePaint)
    return BitmapDrawable(context.resources, bmp)
}

private class MapOverlays {
    var map: MapView? = null
    var track: Polyline? = null
    var circle: Polygon? = null
    var accuracyCircle: Polygon? = null
    var anchor: Marker? = null
    var boat: Marker? = null
    var anchorIcon: BitmapDrawable? = null
    var boatOkIcon: BitmapDrawable? = null
    var boatAlarmIcon: BitmapDrawable? = null
}
