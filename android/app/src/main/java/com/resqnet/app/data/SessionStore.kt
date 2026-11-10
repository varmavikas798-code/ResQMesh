package com.resqnet.app.data

import android.content.Context

data class User(
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val qualification: String
)

/** Tiny SharedPreferences-backed store for the registered user and login session. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("resqnet_session", Context.MODE_PRIVATE)

    fun saveUser(user: User) {
        prefs.edit()
            .putString("name", user.name)
            .putString("email", user.email)
            .putString("phone", user.phone)
            .putString("password", user.password)
            .putString("qualification", user.qualification)
            .apply()
    }

    fun getUser(): User? {
        val email = prefs.getString("email", null) ?: return null
        return User(
            name = prefs.getString("name", "") ?: "",
            email = email,
            phone = prefs.getString("phone", "") ?: "",
            password = prefs.getString("password", "") ?: "",
            qualification = prefs.getString("qualification", "CPR Volunteer") ?: "CPR Volunteer"
        )
    }

    fun saveAuthToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getAuthToken(): String? = prefs.getString("auth_token", null)

    fun saveFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    fun getFcmToken(): String? = prefs.getString("fcm_token", null)

    fun clearAuthToken() {
        prefs.edit().remove("auth_token").apply()
    }

    var loggedIn: Boolean
        get() = prefs.getBoolean("logged_in", false)
        set(value) {
            prefs.edit().putBoolean("logged_in", value).apply()
        }

    fun logout() {
        loggedIn = false
    }
}
