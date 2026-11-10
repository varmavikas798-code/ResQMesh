package com.resqnet.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.concurrent.Executor
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
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
    if (provider == null) {
        onResult(null)
        return
    }

    fun lastKnownLocation(): Location? = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val cancellation = CancellationSignal()
        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable { cancellation.cancel(); onResult(lastKnownLocation()) }
        handler.postDelayed(timeout, 15_000)
        manager.getCurrentLocation(provider, cancellation, Executor { command -> handler.post(command) }) { result ->
            handler.removeCallbacks(timeout)
            onResult(result ?: lastKnownLocation())
        }
    } else {
        val listener = object : LocationListener {
            override fun onLocationChanged(result: Location) {
                manager.removeUpdates(this)
                onResult(result)
            }
        }
        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable { manager.removeUpdates(listener); onResult(lastKnownLocation()) }
        handler.postDelayed(timeout, 15_000)
        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
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
    var sosSucceeded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
                                sosMessage = ResQApi.createSos(token, current.latitude, current.longitude)
                                sosSucceeded = true
                                val time = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
                                val coordinates = "%.5f, %.5f".format(Locale.US, current.latitude, current.longitude)
                                onSosSent(AlertRecord("SOS request", coordinates, time, "Saved to server"))
                            } catch (error: Exception) {
                                sosSucceeded = false
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
            text = { Text(sosMessage, color = Slate) },
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


