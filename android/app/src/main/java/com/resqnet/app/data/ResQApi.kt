package com.resqnet.app.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(val token: String, val user: User)

class ResQApiException(val statusCode: Int, message: String) : IllegalStateException(message)

object ResQApi {
    // Phones on the current Wi-Fi use the laptop's LAN address. The emulator uses
    // its host bridge so local Android Studio runs continue to work as well.
    private const val PHONE_API_URL = "http://192.168.29.40:8000"
    private const val EMULATOR_API_URL = "http://10.0.2.2:8000"
    private val BASE_URL = if (
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.MODEL.contains("emulator", ignoreCase = true) ||
        Build.MODEL.startsWith("sdk_gphone", ignoreCase = true) ||
        Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true)
    ) EMULATOR_API_URL else PHONE_API_URL

    suspend fun register(user: User): String {
        val apiPassword = bcryptCompatiblePassword(user.password)
        val result = request(
            "POST", "/auth/register",
            JSONObject()
                .put("name", user.name)
                .put("email", user.email)
                .put("phone", user.phone.filter(Char::isDigit))
                .put("password", apiPassword)
                .put("qualification", user.qualification)
        )
        return result.optString("demo_otp").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("The server did not return a demo verification code.")
    }

    suspend fun verify(email: String, otp: String): AuthSession = parseSession(
        request("POST", "/auth/verify", JSONObject().put("email", email).put("otp", otp))
    )

    suspend fun login(email: String, password: String): AuthSession = parseSession(
        request("POST", "/auth/login", JSONObject().put("email", email).put("password", bcryptCompatiblePassword(password)))
    )

    suspend fun currentUser(token: String): User = parseUser(request("GET", "/me", JSONObject(), token))

    /** Migrate a pre-backend local account, or sign into an account already present on the API. */
    suspend fun syncLocalAccount(user: User): AuthSession {
        return try {
            val otp = register(user)
            verify(user.email, otp)
        } catch (error: ResQApiException) {
            if (error.statusCode != 409) throw error
            login(user.email, user.password)
        }
    }

    suspend fun setResponderLocation(token: String, latitude: Double, longitude: Double) {
        request(
            "PUT", "/responders/location",
            JSONObject().put("latitude", latitude).put("longitude", longitude),
            token
        )
    }

    suspend fun createSos(token: String, latitude: Double, longitude: Double): String {
        val result = request(
            "POST", "/sos",
            JSONObject()
                .put("latitude", latitude)
                .put("longitude", longitude)
                .put("message", "Emergency assistance needed"),
            token
        )
        val notified = result.optInt("responders_notified", 0)
        return "SOS saved by the server. Nearby responders found: $notified. Push/SMS delivery is not configured yet."
    }

    suspend fun updateFcmToken(token: String, authToken: String) {
        request(
            "PUT", "/fcm/token",
            JSONObject().put("fcm_token", token),
            authToken
        )
    }

    private fun parseSession(json: JSONObject): AuthSession {
        return AuthSession(json.getString("token"), parseUser(json.getJSONObject("user")))
    }

    private fun parseUser(profile: JSONObject): User {
        return User(
            name = profile.optString("name"),
            email = profile.optString("email"),
            phone = profile.optString("phone"),
            password = "",
            qualification = profile.optString("qualification", "CPR Volunteer")
        )
    }

    /** Keep long local passwords intact while adapting them to bcrypt's 72-byte input limit. */
    private fun bcryptCompatiblePassword(password: String): String {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        if (passwordBytes.size <= 72) return password

        // A hexadecimal SHA-256 digest is 64 ASCII bytes, preserving the full input entropy
        // without sending the long password to a bcrypt-backed API.
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(passwordBytes)
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private suspend fun request(
        method: String,
        path: String,
        payload: JSONObject,
        token: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 8_000
            doOutput = method !in setOf("GET", "HEAD")
            setRequestProperty("Content-Type", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        try {
            if (connection.doOutput) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                throw ResQApiException(status, detail?.takeIf(String::isNotBlank) ?: "Server returned HTTP $status")
            }
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
