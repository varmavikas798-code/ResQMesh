package com.resqnet.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqnet.app.data.EmergencyStore
import com.resqnet.app.data.SosResponder
import com.resqnet.app.data.User
import com.resqnet.app.data.ResQApi
import com.resqnet.app.ui.components.LogoBadge
import com.resqnet.app.ui.theme.BlueBg
import com.resqnet.app.ui.theme.BlueText
import com.resqnet.app.ui.theme.GreenBg
import com.resqnet.app.ui.theme.GreenBorder
import com.resqnet.app.ui.theme.GreenText
import com.resqnet.app.ui.theme.Line
import com.resqnet.app.ui.theme.Mist
import com.resqnet.app.ui.theme.Navy
import com.resqnet.app.ui.theme.Red
import com.resqnet.app.ui.theme.RedDark
import com.resqnet.app.ui.theme.RedLight
import com.resqnet.app.ui.theme.Slate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

data class AlertRecord(val title: String, val location: String, val time: String, val status: String)

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Responders("Responders", Icons.Outlined.Groups),
    History("History", Icons.Outlined.History),
    Profile("Profile", Icons.Outlined.Person)
}

@Suppress("MissingPermission")
private fun fetchCurrentLocation(context: Context, onResult: (Location?) -> Unit) {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    val handler = Handler(Looper.getMainLooper())
    var delivered = false

    fun deliver(result: Location?) {
        if (delivered) return
        delivered = true
        onResult(result)
    }

    // Safety net: if the fused provider stalls (rare), fall back to the last
    // cached fix after a short wait so the UI never spins forever.
    handler.postDelayed({ deliver(null) }, 12_000)

    // First try a fresh high-accuracy fix; if that yields nothing, use the
    // last known fix (the fused provider blends GPS with Wi-Fi/cell so it
    // usually succeeds even indoors).
    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { fresh ->
            if (fresh != null) deliver(fresh)
            else fused.lastLocation
                .addOnSuccessListener { cached -> deliver(cached) }
                .addOnFailureListener { deliver(null) }
        }
        .addOnFailureListener {
            fused.lastLocation
                .addOnSuccessListener { cached -> deliver(cached) }
                .addOnFailureListener { deliver(null) }
        }
}

@Composable
fun MainScreen(
    user: User,
    authToken: String?,
    accountSyncError: String?,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.Home) }
    val history = remember { mutableStateListOf<AlertRecord>() }
    var currentLocation by remember { mutableStateOf<Location?>(null) }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Red,
                            selectedTextColor = Red,
                            indicatorColor = RedLight,
                            unselectedIconColor = Slate,
                            unselectedTextColor = Slate
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.Home -> HomeTab(
                    user = user,
                    authToken = authToken,
                    accountSyncError = accountSyncError,
                    location = currentLocation,
                    onLocationChanged = { currentLocation = it },
                    onSosSent = { history.add(0, it) }
                )
                Tab.Responders -> RespondersTab(authToken = authToken, location = currentLocation)
                Tab.History -> HistoryTab(history)
                Tab.Profile -> ProfileTab(user, currentLocation, onLogout)
            }
        }
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, border: Color? = null) {
    Text(
        text,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun ScreenHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            trailing()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
private fun HomeTab(
    user: User,
    authToken: String?,
    accountSyncError: String?,
    location: Location?,
    onLocationChanged: (Location?) -> Unit,
    onSosSent: (AlertRecord) -> Unit
) {
    val context = LocalContext.current
    var locationMessage by remember { mutableStateOf("Location permission is needed to get your current position.") }
    var locationLoading by remember { mutableStateOf(false) }
    var requestLocation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            requestLocation?.invoke()
        } else {
            locationMessage = "Location permission was denied. Enable it in Settings to use SOS."
            locationLoading = false
        }
    }
    requestLocation = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            locationLoading = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            locationLoading = true
            locationMessage = "Getting current location…"
            fetchCurrentLocation(context) { result ->
                onLocationChanged(result)
                locationMessage = if (result == null) "Couldn't get a GPS fix. Check that Location is turned on and try again." else "Current location"
                locationLoading = false
            }
        }
    }
    LaunchedEffect(Unit) { requestLocation?.invoke() }
    var locationSyncMessage by remember { mutableStateOf("") }
    LaunchedEffect(location?.latitude, location?.longitude, authToken) {
        val current = location
        val token = authToken
        if (current != null && token != null) {
            locationSyncMessage = "Saving responder location to server…"
            try {
                ResQApi.setResponderLocation(token, current.latitude, current.longitude)
                locationSyncMessage = "Responder location saved to server"
            } catch (error: Exception) {
                locationSyncMessage = "Location sync failed: ${error.message ?: "server unavailable"}"
            }
        }
    }
    var showConfirm by remember { mutableStateOf(false) }
    var showSent by remember { mutableStateOf(false) }
    var sendingSos by remember { mutableStateOf(false) }
    var sosMessage by remember { mutableStateOf("") }
    var sosResponders by remember { mutableStateOf<List<SosResponder>>(emptyList()) }
    var sosSucceeded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val activeEmergency by EmergencyStore.active.collectAsState()
    val emergencyFresh = activeEmergency != null && System.currentTimeMillis() - activeEmergency!!.receivedAt < 2 * 60 * 60 * 1000

    // Reliability setup — makes sure alarms arrive on every phone brand
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    val notificationsAllowed = if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    var showReliability by remember { mutableStateOf(false) }
    val brandSteps = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> "• Security app → Permissions → Autostart → enable ResQNet\n• Battery & performance → App battery saver → ResQNet → No restrictions"
        "oppo", "realme" -> "• Phone Manager → App startup → allow ResQNet to start in background\n• Battery → ResQNet → Allow background activity"
        "vivo", "iqoo" -> "• i Manager → App manager → Autostart → enable ResQNet\n• Battery → High background power consumption → allow ResQNet"
        "samsung" -> "• Settings → Battery → Background usage limits → remove ResQNet from Sleeping apps\n• Settings → Apps → ResQNet → Battery → Unrestricted"
        "oneplus" -> "• Settings → Battery → Battery optimization → ResQNet → Don't optimize"
        "honor", "huawei" -> "• Settings → Battery → App launch → ResQNet → Manage manually → enable all three"
        else -> "• Settings → Apps → ResQNet → Battery → Unrestricted / Allow background activity"
    }
    val firstName = user.name.trim().substringBefore(" ").ifBlank { "there" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoBadge(size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text("ResQNet", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(42.dp).background(Mist, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Navy)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Greeting card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEAFAF0), RoundedCornerShape(18.dp))
                    .border(1.dp, GreenBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Hello, $firstName!", color = GreenText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Your safety is our priority.", color = GreenText, fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier
                    .background(if (location != null) Color(0xFF3DB26E) else Slate, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.LocationOn, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (locationLoading) "GPS…" else if (location != null) "GPS" else "GPS off", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Location
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mist, RoundedCornerShape(14.dp))
                    .border(1.dp, Line, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.LocationOn, null, tint = Red)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(location?.let { "%.5f, %.5f".format(Locale.US, it.latitude, it.longitude) } ?: "Location unavailable", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(locationMessage, color = Slate, fontSize = 12.sp)
                    if (locationSyncMessage.isNotBlank()) Text(locationSyncMessage, color = if (locationSyncMessage.startsWith("Location sync failed")) Red else GreenText, fontSize = 11.sp)
                    if (accountSyncError != null) Text(accountSyncError, color = Red, fontSize = 11.sp)
                }
                Text(if (locationLoading) "…" else "Refresh", color = Red, fontSize = 14.sp, modifier = Modifier.clickable { requestLocation?.invoke() })
            }

            // Emergency near you — shown to responders when an SOS push arrives
            if (emergencyFresh && activeEmergency != null) {
                val emergency = activeEmergency!!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF4E5), RoundedCornerShape(18.dp))
                        .border(2.dp, Color(0xFFE8890C), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text("🚨 EMERGENCY NEAR YOU", color = Color(0xFFB25E00), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${emergency.requester} needs help ${"%.1f".format(java.util.Locale.US, emergency.distanceKm)} km away",
                        color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Emergency location: %.4f, %.4f".format(java.util.Locale.US, emergency.latitude, emergency.longitude),
                        color = Slate, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "🧭 NAVIGATE",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0xFF1F7A4A), RoundedCornerShape(10.dp))
                                .clickable {
                                    try {
                                        val uri = android.net.Uri.parse("geo:${emergency.latitude},${emergency.longitude}?q=${emergency.latitude},${emergency.longitude}(Emergency+location)")
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                                    } catch (_: Exception) {}
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        Text(
                            "DISMISS",
                            color = Slate,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Mist, RoundedCornerShape(10.dp))
                                .clickable { EmergencyStore.clear() }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Reliability setup — brand-specific guidance so alarms always arrive
            if (!batteryExempt || !notificationsAllowed) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFFBEA), RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFFE3B341), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Text("⚡ ALARM-PROOF YOUR PHONE", color = Color(0xFF9C6A00), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (!notificationsAllowed) "Notifications are blocked — SOS alarms can't reach you."
                        else "Battery optimization may delay emergency alarms on this phone.",
                        color = Slate, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "FIX NOW",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFE8890C), RoundedCornerShape(10.dp))
                            .clickable { showReliability = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // SOS card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(RedLight, Color.White)), RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFFF8D5DA), RoundedCornerShape(22.dp))
                    .padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .shadow(18.dp, CircleShape, ambientColor = Red, spotColor = Red)
                        .size(164.dp)
                        .background(Color.White, CircleShape)
                        .padding(6.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFFD63A4F), RedDark)), CircleShape)
                        .clickable { showConfirm = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Warning, null, tint = Color.White, modifier = Modifier.size(42.dp))
                        Text("SOS", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Tap for Help", color = Color.White, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Press in an emergency to alert nearby responders",
                    color = Slate,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Connection status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mist, RoundedCornerShape(18.dp))
                    .border(1.dp, Line, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("CONNECTION STATUS", color = Slate, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                StatusRow(Icons.Outlined.Wifi, "Internet", "Available", GreenBg, GreenText)
                StatusRow(Icons.Outlined.SignalCellularAlt, "SMS", "Available", GreenBg, GreenText)
                StatusRow(Icons.Outlined.Bluetooth, "Bluetooth", "Active", BlueBg, BlueText)
            }
        }
    }

    if (showReliability) {
        AlertDialog(
            onDismissRequest = { showReliability = false },
            containerColor = Color.White,
            title = { Text("Make alarms 100% reliable", color = Navy, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (!notificationsAllowed) {
                        Text("1. Turn ON notifications", color = Navy, fontWeight = FontWeight.Bold)
                        TextButton(onClick = {
                            try {
                                context.startActivity(
                                    android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            } catch (_: Exception) {}
                        }) { Text("Open notification settings", color = Red) }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("2. Allow alarms in battery settings", color = Navy, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {}
                    }) { Text("Allow background alarms", color = Red) }
                    Spacer(Modifier.height(8.dp))
                    Text("3. ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} extra steps", color = Navy, fontWeight = FontWeight.Bold)
                    Text(brandSteps, color = Slate, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showReliability = false }) { Text("DONE", color = Red, fontWeight = FontWeight.Bold) }
            }
        )
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = Color.White,
            title = { Text("Send SOS alert?", color = Navy, fontWeight = FontWeight.Bold) },
            text = { Text(if (location != null) "Your current coordinates will be included with this SOS." else "ResQNet needs your current location before it can send an SOS.", color = Slate) },
            confirmButton = {
                TextButton(onClick = {
                    if (location == null) {
                        showConfirm = false
                        requestLocation?.invoke()
                    } else {
                        showConfirm = false
                        sendingSos = true
                        scope.launch {
                            val current = location
                            try {
                                if (current == null) throw IllegalStateException("Location is no longer available. Refresh it and try again.")
                                val token = authToken ?: throw IllegalStateException("Account is still syncing. Try again in a moment.")
                                val sosResult = ResQApi.createSos(token, current.latitude, current.longitude)
                                sosResponders = sosResult.responders
                                sosMessage = "Nearby responders found: ${sosResult.notified}. ${sosResult.delivery}."
                                sosSucceeded = true
                                val time = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
                                val coordinates = "%.5f, %.5f".format(Locale.US, current.latitude, current.longitude)
                                onSosSent(AlertRecord("SOS request", coordinates, time, "Saved to server"))
                            } catch (error: Exception) {
                                sosSucceeded = false
                                sosResponders = emptyList()
                                sosMessage = error.message ?: "Couldn't reach the ResQMesh server."
                            } finally {
                                sendingSos = false
                                showSent = true
                            }
                        }
                    }
                }) { Text(if (location == null) "GET MY LOCATION" else "SEND SOS", color = Red, fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel", color = Slate) }
            }
        )
    }
    if (showSent) {
        AlertDialog(
            onDismissRequest = { showSent = false },
            containerColor = Color.White,
            title = { Text(if (sosSucceeded) "SOS request received" else "SOS request failed", color = if (sosSucceeded) GreenText else Red, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(sosMessage, color = Slate)
                    if (sosSucceeded && sosResponders.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("RESPONDER RADAR — 3 KM RANGE", color = Slate, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .background(Color(0xFFF2F4F9), RoundedCornerShape(14.dp))
                        ) {
                            val center = Offset(size.width / 2, size.height / 2)
                            val radarRadius = size.minDimension / 2 - 12.dp.toPx()
                            drawCircle(Red.copy(alpha = 0.07f), radarRadius, center)
                            drawCircle(Red.copy(alpha = 0.25f), radarRadius * 0.66f, center, style = Stroke(1.dp.toPx()))
                            drawCircle(Red.copy(alpha = 0.25f), radarRadius * 0.33f, center, style = Stroke(1.dp.toPx()))
                            // You, at the center of the emergency
                            drawCircle(Red, 9.dp.toPx(), center)
                            val centerLat = location?.latitude ?: 0.0
                            val centerLon = location?.longitude ?: 0.0
                            sosResponders.forEach { responder ->
                                val dLat = responder.latitude - centerLat
                                val dLon = responder.longitude - centerLon
                                val bearing = Math.atan2(dLon, dLat)
                                val distanceFraction = (responder.distanceKm / 3.0).coerceIn(0.18, 1.0)
                                val position = Offset(
                                    center.x + (Math.sin(bearing) * radarRadius * distanceFraction).toFloat(),
                                    center.y - (Math.cos(bearing) * radarRadius * distanceFraction).toFloat()
                                )
                                drawCircle(Color(0xFF1F7A4A), 7.dp.toPx(), position)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("● You   ● Responders (green)", color = Slate, fontSize = 11.sp)
                        Spacer(Modifier.height(10.dp))
                        sosResponders.forEach { responder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Mist, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(10.dp).background(Color(0xFF1F7A4A), CircleShape)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(responder.name, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(responder.qualification, color = Slate, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("%.1f km".format(java.util.Locale.US, responder.distanceKm), color = Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("%.4f, %.4f".format(java.util.Locale.US, responder.latitude, responder.longitude), color = Slate, fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    if (sosSucceeded && sosResponders.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("No responders within 3 km right now — your SOS stays active and alerts anyone who comes near.", color = Slate, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSent = false }) { Text("OK", color = Red, fontWeight = FontWeight.Bold) }
            }
        )
    }
}

@Composable
private fun StatusRow(icon: ImageVector, label: String, status: String, bg: Color, fg: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Navy, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Chip(status, bg, fg)
    }
}

@Composable
fun Avatar(initials: String, size: Dp, bg: Color, fg: Color, fontSize: Int) {
    Box(
        modifier = Modifier.size(size).background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp)
    }
}


