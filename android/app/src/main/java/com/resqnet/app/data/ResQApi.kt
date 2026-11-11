package com.resqnet.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(val token: String, val user: User)

class ResQApiException(val statusCode: Int, message: String) : IllegalStateException(message)

object ResQApi {
    private const val BASE_URL = "https://resqmesh-api-3g1c.onrender.com"

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

    /** Live list of available responders within 3 km of the given point. */
    suspend fun nearbyResponders(token: String, latitude: Double, longitude: Double): JSONArray {
        val body = rawRequest(
            "GET",
            "/responders/nearby?lat=$latitude&lon=$longitude",
            JSONObject(),
            token
        )
        return if (body.isBlank()) JSONArray() else JSONArray(body)
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
        val delivery = result.optString("delivery").takeIf { it.isNotBlank() }
            ?: "Delivery status unknown"
        return "SOS saved by the server. Nearby responders found: $notified. $delivery."
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
    ): JSONObject {
        val body = rawRequest(method, path, payload, token)
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private suspend fun rawRequest(
        method: String,
        path: String,
        payload: JSONObject,
        token: String? = null
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            // Free-tier hosting (Render) sleeps after inactivity and can take 30-60s
            // to wake. Longer timeouts let the first call survive the cold start.
            connectTimeout = 20_000
            readTimeout = 45_000
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
            body
        } finally {
            connection.disconnect()
        }
    }
}
