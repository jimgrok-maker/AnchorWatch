package com.jimgrok.anchorwatch.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jimgrok.anchorwatch.data.GeoFix
import com.jimgrok.anchorwatch.data.WatchState
import com.jimgrok.anchorwatch.data.WatchStore
import com.jimgrok.anchorwatch.data.isRadiusTooSmallForGps
import com.jimgrok.anchorwatch.service.AnchorWatchService
import kotlin.math.roundToInt

private const val TAG = "WatchScreen"

data class SystemReadiness(
    val gps: Boolean,
    val background: Boolean,
    val battery: Boolean,
    /** Android 14+ can revoke the full-screen alarm intent; always true below API 34. */
    val fullScreen: Boolean = true
) {
    val allGood: Boolean get() = gps && background && battery && fullScreen

    companion object {
        fun from(context: Context): SystemReadiness {
            val fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val background = if (Build.VERSION.SDK_INT >= 29) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                fine || coarse
            }
            val notifyOk = if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            val lm = context.getSystemService(LocationManager::class.java)
            val providerOn = runCatching {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)
            val pm = context.getSystemService(PowerManager::class.java)
            val battery = runCatching {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(false)
            val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                }.getOrDefault(false)
            } else {
                true
            }
            return SystemReadiness(
                gps = fine && providerOn,
                background = background && notifyOk,
                battery = battery,
                fullScreen = fullScreen
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun WatchScreen() {
    val context = LocalContext.current
    val state by WatchStore.state.collectAsStateWithLifecycle()
    var followBoat by rememberSaveable { mutableStateOf(true) }
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }
    var recenterToken by remember { mutableIntStateOf(0) }
    val dwellSec = (state.dwellMs / 1000L).toInt()
    var permissionNote by remember { mutableStateOf<String?>(null) }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var readiness by remember { mutableStateOf(SystemReadiness.from(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val fine = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        locationGranted = fine
        readiness = SystemReadiness.from(context)
        permissionNote = if (fine) null else "Precise GPS location permission is required for an anchor watch."
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                readiness = SystemReadiness.from(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    // Only run the UI preview GPS listener when not actively on watch (avoid duplicating the service's GPS listener).
    DisposableEffect(locationGranted, state.watching) {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!WatchStore.snapshot().watching) {
                    WatchStore.previewFix(location.toFix())
                }
            }

            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    WatchStore.setGpsEnabled(true)
                    readiness = SystemReadiness.from(context)
                }
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    WatchStore.setGpsEnabled(false)
                    readiness = SystemReadiness.from(context)
                }
            }
        }
        if (locationGranted && !state.watching) {
            seedLastKnown(lm)
            try {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1500L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (e: Exception) {
                Log.w(TAG, "requestLocationUpdates(gps) failed", e)
            }
        }
        onDispose {
            try {
                lm.removeUpdates(listener)
            } catch (e: Exception) {
                Log.w(TAG, "removeUpdates failed", e)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnchorColors.Canvas)
    ) {
        OsmMap(
            state = state,
            followBoat = followBoat,
            recenterToken = recenterToken,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            StatusCard(
                state = state,
                permissionNote = permissionNote,
                readiness = readiness,
                onGpsLamp = {
                    val fine = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!fine) openAppSettings(context)
                    else runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                },
                onBgLamp = { openAppSettings(context) },
                onFullScreenLamp = { openFullScreenIntentSettings(context) },
                onBatteryLamp = { openBatterySettings(context) }
            )

            // Floating map action pills on the right edge between StatusCard and ControlsCard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapActionPill(
                        icon = Icons.Default.GpsFixed,
                        contentDescription = "Recenter map",
                        active = false,
                        onClick = { recenterToken++ }
                    )
                    MapActionPill(
                        icon = Icons.Default.MyLocation,
                        contentDescription = if (followBoat) "Following boat" else "Free pan",
                        active = followBoat,
                        onClick = {
                            followBoat = !followBoat
                            if (followBoat) recenterToken++
                        }
                    )
                }
            }

            ControlsCard(
                state = state,
                dwellSec = dwellSec,
                expanded = controlsExpanded,
                onToggleExpanded = { controlsExpanded = !controlsExpanded },
                onDwell = { WatchStore.setDwellMs(it * 1000L) },
                followBoat = followBoat,
                onFollow = {
                    followBoat = it
                    if (it) recenterToken++
                },
                onRadius = { WatchStore.setRadius(it) },
                onUnits = { WatchStore.setUseFeet(it) },
                onDrop = { AnchorWatchService.start(context, state.radiusFt, state.dwellMs) },
                onWeigh = { AnchorWatchService.stop(context) },
                onSilence = { AnchorWatchService.silence(context) },
                onTest = { AnchorWatchService.testAlarm(context) },
                onBattery = { openBatterySettings(context) },
                onLocationSettings = { openAppSettings(context) }
            )
        }
    }
}

@Composable
private fun MapActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) AnchorColors.AmberSoft.copy(alpha = 0.95f) else AnchorColors.Surface.copy(alpha = 0.92f),
        border = BorderStroke(
            1.dp,
            if (active) AnchorColors.Amber.copy(alpha = 0.7f) else AnchorColors.BorderStrong
        ),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (active) AnchorColors.Amber else AnchorColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatusCard(
    state: WatchState,
    permissionNote: String?,
    readiness: SystemReadiness,
    onGpsLamp: () -> Unit,
    onBgLamp: () -> Unit,
    onFullScreenLamp: () -> Unit,
    onBatteryLamp: () -> Unit
) {
    val statusLabel = when {
        state.alarming && state.gpsLost -> "GPS LOST"
        state.alarming -> "DRAGGING"
        state.gpsLost -> "GPS LOST"
        state.silenced -> "SILENCED"
        state.waitingForHook -> "WAITING"
        state.watching -> "ON WATCH"
        else -> "READY"
    }
    val statusColor = when {
        state.alarming || state.gpsLost -> AnchorColors.Coral
        state.silenced || state.waitingForHook -> AnchorColors.Amber
        state.watching -> AnchorColors.Emerald
        else -> AnchorColors.Amber
    }
    val statusBg = when {
        state.alarming || state.gpsLost -> AnchorColors.CoralSoft
        state.watching && !state.silenced && !state.waitingForHook -> AnchorColors.EmeraldSoft
        else -> AnchorColors.AmberSoft
    }
    val cardBorder = when {
        state.alarming || state.gpsLost -> AnchorColors.Coral.copy(alpha = 0.7f)
        state.watching -> AnchorColors.Emerald.copy(alpha = 0.45f)
        else -> AnchorColors.BorderStrong
    }

    val distLabel = if (state.watching && !state.waitingForHook) {
        formatDistance(state.distanceFt, state.useFeet)
    } else {
        "—"
    }
    val radiusLabel = formatDistance(state.radiusFt.toDouble(), state.useFeet)
    val accFt = (state.boat?.accuracyM ?: 0f) * WatchState.M_TO_FT
    val accLabel = if (state.boat != null && state.boat.accuracyM > 0f) {
        "±${formatDistance(accFt.toDouble(), state.useFeet)}"
    } else {
        "—"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AnchorColors.Surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusBg,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                ReadinessBox(
                    readiness = readiness,
                    onGps = onGpsLamp,
                    onBg = onBgLamp,
                    onFullScreen = onFullScreenLamp,
                    onBattery = onBatteryLamp
                )
            }

            Spacer(Modifier.height(10.dp))

            // 3-column cockpit telemetry tiles
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TelemetryTile(
                    label = "FROM HOOK",
                    value = distLabel,
                    valueColor = when {
                        state.alarming -> AnchorColors.Coral
                        state.watching && state.distanceFt > state.radiusFt -> AnchorColors.Coral
                        state.watching && state.distanceFt > state.radiusFt * 0.75 -> AnchorColors.Amber
                        state.watching -> AnchorColors.Emerald
                        else -> AnchorColors.TextSecondary
                    },
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    label = "SWING LIMIT",
                    value = radiusLabel,
                    valueColor = AnchorColors.Amber,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    label = "GPS ERROR",
                    value = accLabel,
                    valueColor = when {
                        !state.gpsEnabled -> AnchorColors.Coral
                        state.boat != null && accFt > state.radiusFt * 0.35 -> AnchorColors.Amber
                        else -> AnchorColors.TextPrimary
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Swing ratio progress bar while anchored
            if (state.watching && !state.waitingForHook) {
                val ratio = (state.distanceFt / state.radiusFt.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
                val barColor = when {
                    state.alarming || state.distanceFt > state.radiusFt -> AnchorColors.Coral
                    ratio >= 0.75f -> AnchorColors.Amber
                    else -> AnchorColors.Emerald
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ratio },
                    color = barColor,
                    trackColor = AnchorColors.SurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
            }

            // Coordinates + contextual subline
            Spacer(Modifier.height(8.dp))
            val subText = when {
                state.gpsLost -> "No accurate GPS fix for 45+ seconds. Check sky view."
                state.waitingForHook -> "Waiting for an accurate GPS fix to drop the hook…"
                state.silenced -> "Alarm silenced until boat returns inside $radiusLabel."
                state.outsideSinceMs != null && !state.alarming -> {
                    val elapsedS = ((System.currentTimeMillis() - state.outsideSinceMs) / 1000L).coerceAtLeast(0L)
                    val totalS = (state.dwellMs / 1000L).coerceAtLeast(1L)
                    "Outside circle (${elapsedS}s / ${totalS}s dwell)…"
                }
                state.boat != null -> {
                    buildString {
                        append("%.5f, %.5f".format(state.boat.latitude, state.boat.longitude))
                        if (!state.gpsEnabled) append("  ·  GPS OFF")
                    }
                }
                else -> if (!state.gpsEnabled) "GPS provider is OFF" else "Acquiring GPS position…"
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (state.boat != null && !state.gpsLost && !state.waitingForHook && state.outsideSinceMs == null && !state.silenced) {
                    FontFamily.Monospace
                } else {
                    FontFamily.Default
                },
                color = if (state.gpsLost || state.outsideSinceMs != null) AnchorColors.Coral else AnchorColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!readiness.allGood) {
                InlineNotice(
                    text = readinessHint(readiness),
                    isError = true
                )
            }
            if (state.watching && state.boat != null && accFt > state.radiusFt * 0.35f) {
                InlineNotice(
                    text = "GPS error is large vs radius. Widen the circle or wait for a tighter fix.",
                    isError = false
                )
            }
            permissionNote?.let {
                InlineNotice(text = it, isError = true)
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AnchorColors.SurfaceCard,
        border = BorderStroke(1.dp, AnchorColors.BorderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = valueColor,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = AnchorColors.TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InlineNotice(text: String, isError: Boolean) {
    val bg = if (isError) AnchorColors.CoralSoft else AnchorColors.AmberSoft
    val accent = if (isError) AnchorColors.Coral else AnchorColors.Amber
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = AnchorColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ReadinessBox(
    readiness: SystemReadiness,
    onGps: () -> Unit,
    onBg: () -> Unit,
    onFullScreen: () -> Unit,
    onBattery: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AnchorColors.Canvas.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, AnchorColors.BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Lamp(label = "GPS", ok = readiness.gps, onClick = onGps)
            Lamp(label = "BG", ok = readiness.background, onClick = onBg)
            Lamp(label = "FSI", ok = readiness.fullScreen, onClick = onFullScreen)
            Lamp(label = "BAT", ok = readiness.battery, onClick = onBattery)
        }
    }
}

@Composable
private fun Lamp(label: String, ok: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (ok) AnchorColors.Emerald else AnchorColors.Coral)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = if (ok) AnchorColors.TextSecondary else AnchorColors.Coral
        )
    }
}

private fun readinessHint(readiness: SystemReadiness): String {
    return when {
        !readiness.gps -> "Tap GPS: allow precise location, or turn the GPS provider on."
        !readiness.background -> "Tap BG: set Location to Allow all the time, and allow notifications."
        !readiness.fullScreen -> "Tap FSI: allow full-screen notifications so the alarm can wake the screen."
        !readiness.battery -> "Tap BAT: allow Ignore battery optimizations so the watch stays alive."
        else -> ""
    }
}

@Composable
private fun ControlsCard(
    state: WatchState,
    dwellSec: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDwell: (Int) -> Unit,
    followBoat: Boolean,
    onFollow: (Boolean) -> Unit,
    onRadius: (Int) -> Unit,
    onUnits: (Boolean) -> Unit,
    onDrop: () -> Unit,
    onWeigh: () -> Unit,
    onSilence: () -> Unit,
    onTest: () -> Unit,
    onBattery: () -> Unit,
    onLocationSettings: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AnchorColors.Surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, AnchorColors.BorderStrong),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top bar: collapse/expand handle + quick unit toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleExpanded)
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Anchor,
                        contentDescription = null,
                        tint = AnchorColors.Amber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (expanded) "Anchor controls" else "Swing ${formatDistance(state.radiusFt.toDouble(), state.useFeet)} · Dwell ${dwellSec}s",
                        style = MaterialTheme.typography.titleSmall,
                        color = AnchorColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    onClick = onToggleExpanded,
                    shape = RoundedCornerShape(999.dp),
                    color = AnchorColors.SurfaceCard,
                    border = BorderStroke(1.dp, AnchorColors.BorderSubtle)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (expanded) "Hide" else "Controls",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnchorColors.TextSecondary
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            contentDescription = if (expanded) "Collapse controls" else "Expand controls",
                            tint = AnchorColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = AnchorColors.BorderSubtle)
                    Spacer(Modifier.height(10.dp))

                    // Swing radius header + segmented ft/m toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "SWING RADIUS",
                                style = MaterialTheme.typography.labelSmall,
                                color = AnchorColors.TextMuted
                            )
                            Text(
                                formatDistance(state.radiusFt.toDouble(), state.useFeet),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnchorColors.Amber
                            )
                        }
                        UnitToggle(useFeet = state.useFeet, onUnits = onUnits)
                    }

                    Slider(
                        value = state.radiusFt.toFloat(),
                        onValueChange = { onRadius(it.roundToInt()) },
                        valueRange = WatchState.MIN_RADIUS_FT.toFloat()..WatchState.MAX_RADIUS_FT.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = AnchorColors.Amber,
                            activeTrackColor = AnchorColors.Amber,
                            inactiveTrackColor = AnchorColors.SurfaceElevated
                        )
                    )

                    if (isRadiusTooSmallForGps(state.radiusFt)) {
                        InlineNotice(
                            text = "Small circle: use ${formatDistance(WatchState.MIN_RECOMMENDED_RADIUS_FT.toDouble(), state.useFeet)} or more to avoid missed fixes and false alarms.",
                            isError = true
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    // Dwell time header + badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "DWELL BEFORE ALARM",
                            style = MaterialTheme.typography.labelSmall,
                            color = AnchorColors.TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AnchorColors.SurfaceCard,
                            border = BorderStroke(1.dp, AnchorColors.BorderSubtle)
                        ) {
                            Text(
                                "${dwellSec}s",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelMedium,
                                color = AnchorColors.Amber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Slider(
                        value = dwellSec.toFloat(),
                        onValueChange = { onDwell(it.roundToInt().coerceIn(3, 20)) },
                        valueRange = 3f..20f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = AnchorColors.Amber,
                            activeTrackColor = AnchorColors.Amber,
                            inactiveTrackColor = AnchorColors.SurfaceElevated
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = AnchorColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Follow boat on chart",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AnchorColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = followBoat,
                            onCheckedChange = onFollow,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AnchorColors.AmberOn,
                                checkedTrackColor = AnchorColors.Amber,
                                uncheckedThumbColor = AnchorColors.TextSecondary,
                                uncheckedTrackColor = AnchorColors.SurfaceElevated,
                                uncheckedBorderColor = AnchorColors.BorderStrong
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Silence button for active drag alarm or transient 10 s test alarm
            if (state.alarming || state.testAlarming) {
                Button(
                    onClick = onSilence,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnchorColors.Coral,
                        contentColor = AnchorColors.CoralOn
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.VolumeOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.alarming) "SILENCE ALARM" else "SILENCE TEST",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Primary Watch CTA
            if (state.watching) {
                Button(
                    onClick = onWeigh,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnchorColors.CoralSoft,
                        contentColor = AnchorColors.Coral
                    ),
                    border = BorderStroke(1.dp, AnchorColors.Coral.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("STOP WATCH", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            } else {
                Button(
                    onClick = onDrop,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnchorColors.Amber,
                        contentColor = AnchorColors.AmberOn
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("START WATCH", fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTest,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AnchorColors.BorderStrong),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = AnchorColors.Amber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Test", style = MaterialTheme.typography.labelMedium, color = AnchorColors.TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onLocationSettings,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AnchorColors.BorderStrong),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = AnchorColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Location", style = MaterialTheme.typography.labelMedium, color = AnchorColors.TextPrimary)
                    }
                    OutlinedButton(
                        onClick = onBattery,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AnchorColors.BorderStrong),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = AnchorColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Battery", style = MaterialTheme.typography.labelMedium, color = AnchorColors.TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitToggle(useFeet: Boolean, onUnits: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AnchorColors.SurfaceCard,
        border = BorderStroke(1.dp, AnchorColors.BorderSubtle)
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            UnitChip(label = "ft", selected = useFeet, onClick = { if (!useFeet) onUnits(true) })
            UnitChip(label = "m", selected = !useFeet, onClick = { if (useFeet) onUnits(false) })
        }
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) AnchorColors.Amber else Color.Transparent
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) AnchorColors.AmberOn else AnchorColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

// Input is always feet. useFeet only selects the display unit.
private fun formatDistance(feet: Double, useFeet: Boolean): String {
    return if (useFeet) "${feet.roundToInt()} ft" else "${(feet * WatchState.FT_TO_M).roundToInt()} m"
}

private fun Location.toFix(): GeoFix = GeoFix(
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracy,
    timeMs = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
    speedMps = if (hasSpeed()) speed else 0f,
    bearing = if (hasBearing()) bearing else 0f
)

@SuppressLint("MissingPermission")
private fun seedLastKnown(lm: LocationManager) {
    val gps = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
    val fallback = gps ?: listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
    fallback?.let { WatchStore.previewFix(it.toFix()) }
}

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(intent) }
        .onFailure { Log.w(TAG, "battery optimization intent failed", it) }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }.onSuccess { return }
            .onFailure { Log.w(TAG, "full-screen intent settings unavailable", it) }
    }
    openAppSettings(context)
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }.onFailure { Log.w(TAG, "openAppSettings failed", it) }
}
