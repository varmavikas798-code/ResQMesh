package com.resqnet.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqnet.app.data.User
import com.resqnet.app.ui.theme.BlueBg
import com.resqnet.app.ui.theme.BlueText
import com.resqnet.app.ui.theme.GreenBg
import com.resqnet.app.ui.theme.GreenText
import com.resqnet.app.ui.theme.Line
import com.resqnet.app.ui.theme.Mist
import com.resqnet.app.ui.theme.Navy
import com.resqnet.app.ui.theme.Red
import com.resqnet.app.ui.theme.RedLight
import com.resqnet.app.ui.theme.Slate

private data class Responder(
    val initials: String,
    val name: String,
    val role: String,
    val distance: String,
    val bg: Color,
    val fg: Color
)

private val responders = listOf(
    Responder("PS", "Dr. Priya Sharma", "Doctor", "1.2 km away", Color(0xFFDDF6E6), Color(0xFF1F7A4A)),
    Responder("NA", "Nurse Anjali", "Nurse", "1.8 km away", Color(0xFFE0E9FF), Color(0xFF2A4FC4)),
    Responder("RK", "Rohit Kumar", "CPR Volunteer", "2.1 km", Color(0xFFF1E4FF), Color(0xFF6A2BC4)),
    Responder("AM", "Arjun Mehta", "Paramedic", "2.7 km", Color(0xFFFFEFD9), Color(0xFFB36A0F))
)

@Composable
fun RespondersTab() {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Nearby Responders") {
            Chip("Within 3 km", RedLight, Red, Color(0xFFF8D5DA))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Radar-style map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(Color(0xFFF2F4F9)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2, size.height / 2)
                    drawCircle(Red.copy(alpha = 0.12f), radius = 80.dp.toPx(), center = c)
                    drawCircle(Red.copy(alpha = 0.55f), radius = 80.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
                    drawCircle(Red, radius = 11.dp.toPx(), center = c)
                    drawCircle(Color(0xFF52B788), radius = 9.dp.toPx(), center = Offset(size.width * 0.2f, size.height * 0.22f))
                    drawCircle(Color(0xFF4F7CE8), radius = 9.dp.toPx(), center = Offset(size.width * 0.8f, size.height * 0.78f))
                    drawCircle(Color(0xFFB77CE8), radius = 9.dp.toPx(), center = Offset(size.width * 0.7f, size.height * 0.2f))
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                responders.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Mist, RoundedCornerShape(18.dp))
                            .border(1.dp, Line, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(r.initials, 48.dp, r.bg, r.fg, 16)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("${r.role} • ${r.distance}", color = Slate, fontSize = 14.sp)
                        }
                        Chip("Available", GreenBg, GreenText)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(history: List<AlertRecord>) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Alert History")
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            history.forEach { h ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Mist, RoundedCornerShape(18.dp))
                        .border(1.dp, Line, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(44.dp).background(RedLight, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Warning, null, tint = Red)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${h.location} • ${h.time}", color = Slate, fontSize = 13.sp)
                    }
                    Chip(h.status, BlueBg, BlueText)
                }
            }
        }
    }
}

@Composable
fun ProfileTab(user: User, location: android.location.Location?, onLogout: () -> Unit) {
    val initials = user.name.trim().split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "R" }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("My Profile") {
            Text("Edit", color = Red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(initials, 104.dp, Color(0xFFE9ECF2), Navy, 36)
            Spacer(Modifier.height(10.dp))
            Text(user.name, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(user.email, color = Slate, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))

            InfoRow("Phone") {
                Text("+91 ${user.phone}", color = Navy, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
            InfoRow("Location") {
                Text(
                    location?.let { "%.5f, %.5f".format(java.util.Locale.US, it.latitude, it.longitude) } ?: "Not shared",
                    color = Navy,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
            InfoRow("Status") {
                Row(
                    modifier = Modifier
                        .background(GreenBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = GreenText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Verified Responder", color = GreenText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Mist, RoundedCornerShape(18.dp))
                    .border(1.dp, Line, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text("MEDICAL QUALIFICATIONS", color = Slate, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(user.qualification, BlueBg, BlueText)
                    Chip("CPR Trained", GreenBg, GreenText)
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsRow("Notification Settings")
            Spacer(Modifier.height(8.dp))
            SettingsRow("About ResQNet")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RedLight, RoundedCornerShape(16.dp))
                    .clickable { onLogout() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Logout", color = Red, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Red)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Navy, fontSize = 17.sp)
            value()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
private fun SettingsRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mist, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Navy, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = Slate)
    }
}
