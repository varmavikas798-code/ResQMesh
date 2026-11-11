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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    // Push readiness must survive a sleeping free-tier server: read the FCM
    // token locally first, then retry the upload a few times with backoff.
    suspend fun syncFcmToken(token: String) {
        val tag = "ResQPush"
        android.util.Log.w(tag, "syncFcmToken: starting")
        val fcmToken = suspendCoroutine<String?> { cont ->
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    android.util.Log.w(tag, "syncFcmToken: got FCM token (${task.result.take(12)}…)")
                    cont.resume(task.result)
                } else {
                    android.util.Log.w(tag, "syncFcmToken: FAILED to get FCM token", task.exception)
                    cont.resume(null)
                }
            }
        } ?: return
        if (fcmToken.isBlank()) return
        store.saveFcmToken(fcmToken)
        repeat(4) { attempt ->
            try {
                ResQApi.updateFcmToken(fcmToken, token)
                android.util.Log.w(tag, "syncFcmToken: uploaded on attempt ${attempt + 1}")
                return
            } catch (error: Exception) {
                android.util.Log.w(tag, "syncFcmToken: upload attempt ${attempt + 1} failed: ${error.message}")
                delay(8_000)
            }
        }
        android.util.Log.w(tag, "syncFcmToken: all 4 upload attempts failed")
    }

    fun saveSession(session: AuthSession) {
        val safeUser = session.user.copy(password = "")
        store.saveUser(safeUser)
        store.saveAuthToken(session.token)
        store.loggedIn = true
        user = safeUser
        authToken = session.token
        authError = null
        scope.launch { syncFcmToken(session.token) }
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
                // Even if the session check failed (e.g. a sleeping server), keep
                // trying to make this device push-ready for future alerts.
                val retryToken = store.getAuthToken()
                if (retryToken != null) {
                    runCatching { syncFcmToken(retryToken) }
                }
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
