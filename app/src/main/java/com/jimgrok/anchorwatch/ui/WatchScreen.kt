package com.jimgrok.anchorwatch.ui

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.jimgrok.anchorwatch.service.AnchorWatchService
import kotlin.math.roundToInt

private val LampGreen = Color(0xFF2E9F5A)
private val LampRed = Color(0xFFC62828)

data class SystemReadiness(
    val gps: Boolean,
    val background: Boolean,
    val battery: Boolean
) {
    val allGood: Boolean get() = gps && background && battery

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
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
            val pm = context.getSystemService(PowerManager::class.java)
            val battery = runCatching {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(false)
            return SystemReadiness(
                gps = (fine || coarse) && providerOn,
                background = background && notifyOk,
                battery = battery
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun WatchScreen() {
    val context = LocalContext.current
    val state by WatchStore.state.collectAsStateWithLifecycle()
    var followBoat by remember { mutableStateOf(true) }
    var dwellSec by remember { mutableStateOf(8) }
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
        val fine = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationGranted = fine || coarse
        readiness = SystemReadiness.from(context)
        permissionNote = if (locationGranted) null else "Location permission is required for an anchor watch."
    }

    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { readiness = SystemReadiness.from(context) }

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
        val needed = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        permissionLauncher.launch(needed.toTypedArray())
        if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    DisposableEffect(locationGranted) {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { location ->
            if (!WatchStore.snapshot().watching) WatchStore.previewFix(location.toFix())
        }
        if (locationGranted) {
            seedLastKnown(lm)
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
                try {
                    if (lm.isProviderEnabled(provider)) {
                        lm.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
                    }
                } catch (_: Exception) {
                }
            }
        }
        onDispose { try { lm.removeUpdates(listener) } catch (_: Exception) {} }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OsmMap(state = state, followBoat = followBoat, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            StatusCard(
                state = state,
                permissionNote = permissionNote,
                readiness = readiness,
                onGpsLamp = {
                    if (!readiness.gps) runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    openAppSettings(context)
                },
                onBgLamp = { openAppSettings(context) },
                onBatteryLamp = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    runCatching { context.startActivity(intent) }
                }
            )
            Column {
                ControlsCard(
                    state = state,
                    dwellSec = dwellSec,
                    onDwell = { dwellSec = it },
                    followBoat = followBoat,
                    onFollow = { followBoat = it },
                    onRadius = { WatchStore.setRadius(it) },
                    onUnits = { WatchStore.setUseFeet(it) },
                    onDrop = { AnchorWatchService.start(context, state.radiusFt, dwellSec * 1000L) },
                    onWeigh = { AnchorWatchService.stop(context) },
                    onSilence = { AnchorWatchService.silence(context) },
                    onTest = { AnchorWatchService.testAlarm(context) },
                    onBattery = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    onLocationSettings = { openAppSettings(context) }
                )
            }
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
    onBatteryLamp: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        state.alarming -> "DRAGGING"
                        state.watching -> "ON WATCH"
                        else -> "READY"
                    },
                    color = when {
                        state.alarming -> MaterialTheme.colorScheme.error
                        state.watching -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                ReadinessBox(readiness = readiness, onGps = onGpsLamp, onBg = onBgLamp, onBattery = onBatteryLamp)
            }
            Spacer(Modifier.height(6.dp))
            val distLabel = formatDistance(state.distanceFt, state.useFeet)
            val radiusLabel = formatDistance(state.radiusFt.toDouble(), state.useFeet)
            val accFt = (state.boat?.accuracyM ?: 0f) * WatchState.M_TO_FT
            val accLabel = formatDistance(accFt.toDouble(), state.useFeet)
            Text(
                text = when {
                    state.watching -> "$distLabel from hook   /   limit $radiusLabel"
                    state.boat != null -> "Fix locked. Set radius, then START ALARM."
                    else -> "Waiting for a GPS / network fix"
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = buildString {
                    if (state.boat == null) append("No position yet") else append("GPS +-$accLabel")
                    if (!state.gpsEnabled) append("   GPS OFF")
                    state.boat?.let { append("   ${"%.5f".format(it.latitude)}, ${"%.5f".format(it.longitude)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            if (!readiness.allGood) {
                Text(
                    text = readinessHint(readiness),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (state.watching && accFt > state.radiusFt * 0.35f) {
                Text(
                    text = "GPS error is large vs radius. Widen the circle or wait for a better fix.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            permissionNote?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun ReadinessBox(
    readiness: SystemReadiness,
    onGps: () -> Unit,
    onBg: () -> Unit,
    onBattery: () -> Unit
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Lamp(label = "GPS", ok = readiness.gps, onClick = onGps)
            Lamp(label = "BG", ok = readiness.background, onClick = onBg)
            Lamp(label = "BAT", ok = readiness.battery, onClick = onBattery)
        }
    }
}

@Composable
private fun Lamp(label: String, ok: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (ok) LampGreen else LampRed))
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

private fun readinessHint(readiness: SystemReadiness): String {
    return when {
        !readiness.gps -> "Tap GPS: turn on location and allow precise location."
        !readiness.background -> "Tap BG: set Location to Allow all the time, and allow notifications."
        !readiness.battery -> "Tap BAT: allow Ignore battery optimizations so the watch stays alive."
        else -> ""
    }
}

@Composable
private fun ControlsCard(
    state: WatchState,
    dwellSec: Int,
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
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Swing radius", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                TextButton(onClick = { onUnits(!state.useFeet) }) { Text(if (state.useFeet) "ft" else "m") }
            }
            Text(formatDistance(state.radiusFt.toDouble(), state.useFeet), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Slider(value = state.radiusFt.toFloat(), onValueChange = { onRadius(it.roundToInt()) }, valueRange = 25f..500f, steps = 18)
            Text("Rule of thumb: rode length + 2x GPS error. Dwell ${dwellSec}s outside the circle before the alarm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Slider(value = dwellSec.toFloat(), onValueChange = { onDwell(it.roundToInt().coerceIn(3, 20)) }, valueRange = 3f..20f, steps = 16)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Follow boat", modifier = Modifier.weight(1f))
                Switch(checked = followBoat, onCheckedChange = onFollow)
            }
            Spacer(Modifier.height(8.dp))
            if (state.alarming) {
                Button(onClick = onSilence, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.VolumeOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Silence sound")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (state.watching) {
                Button(onClick = onWeigh, modifier = Modifier.fillMaxWidth().height(64.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("STOP ALARM", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDrop,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    enabled = state.boat != null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("START ALARM", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Test alarm sound")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLocationSettings) { Text("All-the-time location") }
                TextButton(onClick = onBattery) { Text("Ignore battery saver") }
            }
        }
    }
}

private fun formatDistance(feet: Double, useFeet: Boolean): String {
    return if (useFeet) "${feet.roundToInt()} ft" else "${(feet * WatchState.FT_TO_M).roundToInt()} m"
}

private fun Location.toFix(): GeoFix = GeoFix(
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracy,
    timeMs = System.currentTimeMillis(),
    speedMps = if (hasSpeed()) speed else 0f,
    bearing = if (hasBearing()) bearing else 0f
)

@SuppressLint("MissingPermission")
private fun seedLastKnown(lm: LocationManager) {
    val candidates = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
    val best = candidates.maxByOrNull { it.time } ?: return
    WatchStore.previewFix(best.toFix())
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
}
