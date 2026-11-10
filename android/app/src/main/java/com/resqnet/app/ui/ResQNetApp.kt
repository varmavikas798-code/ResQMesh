package com.resqnet.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.resqnet.app.data.AuthSession
import com.resqnet.app.data.ResQApi
import com.resqnet.app.data.ResQApiException
import com.resqnet.app.data.SessionStore
import com.resqnet.app.data.User
import com.resqnet.app.ui.screens.LoginScreen
import com.resqnet.app.ui.screens.MainScreen
import com.resqnet.app.ui.screens.OtpScreen
import com.resqnet.app.ui.screens.RegisterScreen
import com.resqnet.app.ui.screens.SplashScreen
import kotlinx.coroutines.launch

private enum class Screen { Splash, Login, Register, Otp, Main }

@Composable
fun ResQNetApp() {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Splash) }
    var pending by remember { mutableStateOf<User?>(null) }
    var pendingOtp by remember { mutableStateOf("") }
    var user by remember { mutableStateOf(store.getUser()) }
    var authToken by remember { mutableStateOf(store.getAuthToken()) }
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    fun syncFcmToken(token: String) {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                if (!fcmToken.isNullOrBlank()) {
                    store.saveFcmToken(fcmToken)
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            ResQApi.updateFcmToken(fcmToken, token)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun saveSession(session: AuthSession) {
        val safeUser = session.user.copy(password = "")
        store.saveUser(safeUser)
        store.saveAuthToken(session.token)
        store.loggedIn = true
        user = safeUser
        authToken = session.token
        authError = null
        syncFcmToken(session.token)
        screen = Screen.Main
    }

    // Accounts created before the app was connected are registered once, then the
    // saved session is checked against the API. Invalid old sessions are migrated
    // when the local password is available, otherwise the user is asked to sign in.
    LaunchedEffect(Unit) {
        val localUser = store.getUser()
        val savedToken = store.getAuthToken()
        if (localUser != null) {
            authBusy = true
            try {
                if (savedToken != null) {
                    try {
                        val remoteUser = ResQApi.currentUser(savedToken)
                        user = remoteUser
                        store.saveUser(remoteUser)
                        store.loggedIn = true
                        syncFcmToken(savedToken)
                    } catch (error: ResQApiException) {
                        if (error.statusCode != 401) throw error
                        if (localUser.password.isNotBlank()) {
                            saveSession(ResQApi.syncLocalAccount(localUser))
                        } else {
                            store.logout()
                            store.clearAuthToken()
                            authError = "Please sign in again to sync this account with the server."
                        }
                    }
                } else if (localUser.password.isNotBlank()) {
                    saveSession(ResQApi.syncLocalAccount(localUser))
                } else {
                    store.logout()
                    authError = "Please sign in to sync this account with the server."
                }
            } catch (error: Exception) {
                authError = "Account sync failed: ${error.message ?: "Check the backend connection and retry."}"
            } finally {
                authBusy = false
            }
        }
    }

    when (screen) {
        Screen.Splash -> SplashScreen(onFinished = {
            screen = when {
                store.loggedIn && store.getUser() != null && store.getAuthToken() != null -> {
                    user = store.getUser()?.copy(password = "")
                    Screen.Main
                }
                store.getUser() != null -> Screen.Login
                else -> Screen.Register
            }
        })

        Screen.Register -> RegisterScreen(
            busy = authBusy,
            serverError = authError,
            onSendOtp = { candidate ->
                pending = candidate
                authError = null
                authBusy = true
                scope.launch {
                    try {
                        pendingOtp = ResQApi.register(candidate)
                        screen = Screen.Otp
                    } catch (error: Exception) {
                        authError = error.message ?: "Registration failed. Check the server connection."
                    } finally {
                        authBusy = false
                    }
                }
            },
            onGoToLogin = { authError = null; screen = Screen.Login }
        )

        Screen.Otp -> OtpScreen(
            phone = pending?.phone ?: "",
            expectedOtp = pendingOtp,
            busy = authBusy,
            serverError = authError,
            onVerified = { code ->
                val candidate = pending
                if (candidate != null) {
                    authBusy = true
                    authError = null
                    scope.launch {
                        try {
                            saveSession(ResQApi.verify(candidate.email, code))
                        } catch (error: Exception) {
                            authError = error.message ?: "Verification failed."
                        } finally {
                            authBusy = false
                        }
                    }
                }
            },
            onResend = {
                pending?.let { candidate ->
                    authBusy = true
                    authError = null
                    scope.launch {
                        try {
                            pendingOtp = ResQApi.register(candidate)
                        } catch (error: Exception) {
                            authError = error.message ?: "Could not resend the code."
                        } finally {
                            authBusy = false
                        }
                    }
                }
            },
            onBack = { authError = null; screen = Screen.Register }
        )

        Screen.Login -> LoginScreen(
            busy = authBusy,
            serverError = authError,
            onLogin = { email, password ->
                authBusy = true
                authError = null
                scope.launch {
                    try {
                        val localUser = store.getUser()?.takeIf { it.email.equals(email.trim(), ignoreCase = true) }
                        val session = if (localUser != null) {
                            ResQApi.syncLocalAccount(localUser.copy(password = password))
                        } else {
                            ResQApi.login(email.trim(), password)
                        }
                        saveSession(session)
                    } catch (error: Exception) {
                        authError = error.message ?: "Sign in failed. Check your details and the server connection."
                    } finally {
                        authBusy = false
                    }
                }
            },
            onGoToRegister = { authError = null; screen = Screen.Register }
        )

        Screen.Main -> MainScreen(
            user = user ?: User("ResQNet User", "", "", "", "CPR Volunteer"),
            authToken = authToken,
            accountSyncError = authError,
            onLogout = {
                store.logout()
                screen = Screen.Login
            }
        )
    }
}
