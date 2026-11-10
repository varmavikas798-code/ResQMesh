package com.resqnet.app.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resqnet.app.data.User
import com.resqnet.app.ui.components.ErrorText
import com.resqnet.app.ui.components.FieldLabel
import com.resqnet.app.ui.components.PrimaryButton
import com.resqnet.app.ui.components.QualificationPicker
import com.resqnet.app.ui.components.ResQField
import com.resqnet.app.ui.components.ResQLogo
import com.resqnet.app.ui.components.Wordmark
import com.resqnet.app.ui.theme.Line
import com.resqnet.app.ui.theme.Navy
import com.resqnet.app.ui.theme.Red
import com.resqnet.app.ui.theme.RedBorder
import com.resqnet.app.ui.theme.RedLight
import com.resqnet.app.ui.theme.Slate

@Composable
private fun AuthContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RedLight, Color.White, Color.White)))
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ResQLogo(size = 92.dp)
            Wordmark(fontSize = 36.sp)
            Text(
                "Offline Emergency Mesh & Responder Network",
                color = Slate,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SwitchAuthLink(prefix: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(prefix, color = Slate, fontSize = 15.sp)
        Text(
            " $action",
            color = Red,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onClick() }
        )
    }
}

@Composable
fun RegisterScreen(
    busy: Boolean = false,
    serverError: String? = null,
    onSendOtp: (User) -> Unit,
    onGoToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var qualification by remember { mutableStateOf("Doctor") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthContainer {
        FieldLabel("Full Name")
        ResQField(name, { name = it }, "Your full name", icon = Icons.Outlined.Person)

        FieldLabel("Gmail Address")
        ResQField(email, { email = it }, "you@gmail.com", icon = Icons.Outlined.Email, keyboardType = KeyboardType.Email)

        FieldLabel("Mobile Number")
        ResQField(
            phone,
            { phone = it.filter(Char::isDigit).take(10) },
            "98765 43210",
            prefixText = "+91",
            keyboardType = KeyboardType.Phone
        )

        FieldLabel("Password")
        ResQField(password, { password = it }, "Min. 6 characters", icon = Icons.Outlined.Lock, isPassword = true)

        FieldLabel("Medical Qualification")
        QualificationPicker(
            options = listOf("Doctor", "Nurse", "Paramedic", "CPR Volunteer"),
            selected = qualification,
            onSelect = { qualification = it }
        )

        ErrorText(serverError ?: error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (busy) "Connecting to server…" else "Send OTP & Register", enabled = !busy) {
            error = when {
                name.isBlank() -> "Please enter your full name."
                !email.trim().matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) -> "Enter a valid Gmail address."
                phone.length != 10 -> "Enter a valid 10-digit mobile number."
                password.length < 6 -> "Password must be at least 6 characters."
                else -> null
            }
            if (error == null) {
                onSendOtp(User(name.trim(), email.trim(), phone, password, qualification))
            }
        }
        SwitchAuthLink("Already registered?", "Sign In", onGoToLogin)
    }
}

@Composable
fun LoginScreen(
    busy: Boolean = false,
    serverError: String? = null,
    onLogin: (String, String) -> Unit,
    onGoToRegister: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthContainer {
        Spacer(Modifier.height(12.dp))
        Text("Welcome back", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Sign in to stay connected with responders.", color = Slate, fontSize = 14.sp)

        FieldLabel("Gmail Address")
        ResQField(email, { email = it }, "you@gmail.com", icon = Icons.Outlined.Email, keyboardType = KeyboardType.Email)

        FieldLabel("Password")
        ResQField(password, { password = it }, "Your password", icon = Icons.Outlined.Lock, isPassword = true)

        ErrorText(serverError ?: error)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(if (busy) "Signing in…" else "Sign In", enabled = !busy) {
            if (email.isBlank() || password.isBlank()) {
                error = "Enter your email and password."
            } else {
                error = null
                onLogin(email, password)
            }
        }
        SwitchAuthLink("New to ResQNet?", "Register", onGoToRegister)
    }
}

@Composable
fun OtpScreen(
    phone: String,
    expectedOtp: String,
    busy: Boolean = false,
    serverError: String? = null,
    onVerified: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AuthContainer {
        Spacer(Modifier.height(12.dp))
        Text("Verify your number", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Enter the 6-digit code sent to +91 $phone", color = Slate, fontSize = 14.sp)

        Text(
            "Demo mode — your OTP is $expectedOtp",
            color = Red,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .background(RedLight, RoundedCornerShape(12.dp))
                .border(1.dp, RedBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        )

        FieldLabel("One-time password")
        ResQField(
            code,
            { code = it.filter(Char::isDigit).take(6) },
            "• • • • • •",
            icon = Icons.Outlined.Lock,
            keyboardType = KeyboardType.NumberPassword
        )

        ErrorText(serverError ?: error)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(if (busy) "Verifying…" else "Verify & Continue", enabled = !busy) {
            if (code.length != 6) error = "Enter the 6-digit code."
            else { error = null; onVerified(code) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Back",
                color = Slate,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onBack() }
            )
            Text(
                "Resend OTP",
                color = Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !busy) { code = ""; error = null; onResend() }
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}
