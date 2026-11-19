package com.resqnet.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthSession(val token: String, val user: User)

/** A responder found near an SOS, with their live position. */
data class SosResponder(
    val name: String,
    val qualification: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double,
    val phone: String
)

data class SosResult(
    val responders: List<SosResponder>,
    val notified: Int,
    val delivery: String
)

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

    suspend fun verifyPhone(token: String, idToken: String): User = parseUser(
        request("POST", "/auth/verify-phone", JSONObject().put("id_token", idToken), token)
    )

    suspend fun requestDemoPhoneOtp(token: String, phone: String): String = request(
        "POST", "/auth/verify-phone-demo/request", JSONObject().put("phone", phone), token
    ).optString("demo_otp")

    suspend fun confirmDemoPhoneOtp(token: String, otp: String): User = parseUser(
        request("POST", "/auth/verify-phone-demo/confirm", JSONObject().put("otp", otp), token)
    )

    suspend fun requestSmsOtp(token: String, phone: String): String = request(
        "POST", "/auth/verify-phone-sms/request", JSONObject().put("phone", phone), token
    ).optString("otp")

    suspend fun confirmSmsOtp(token: String, otp: String): User = parseUser(
        request("POST", "/auth/verify-phone-sms/confirm", JSONObject().put("otp", otp), token)
    )

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

    suspend fun createSos(token: String, latitude: Double, longitude: Double): SosResult {
        val result = request(
            "POST", "/sos",
            JSONObject()
                .put("latitude", latitude)
                .put("longitude", longitude)
                .put("message", "Emergency assistance needed"),
            token
        )
        val array = result.optJSONArray("responders") ?: JSONArray()
        val responders = List(array.length()) { index ->
            val row = array.getJSONObject(index)
            SosResponder(
                name = row.optString("name", "Responder"),
                qualification = row.optString("qualification", "Responder"),
                latitude = row.optDouble("latitude", 0.0),
                longitude = row.optDouble("longitude", 0.0),
                distanceKm = row.optDouble("distance_km", 0.0),
                phone = row.optString("phone", "")
            )
        }
        val notified = result.optInt("responders_notified", 0)
        val delivery = result.optString("delivery").takeIf { it.isNotBlank() }
            ?: "Delivery status unknown"
        return SosResult(responders, notified, delivery)
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
            qualification = profile.optString("qualification", "CPR Volunteer"),
            phoneVerified = profile.optBoolean("phone_verified", false)
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

    /**
     * DNS-resilient HTTP: if the system resolver cannot resolve the emergency
     * server's hostname (common during network outages), fall back to known
     * Render edge IPs. TLS still verifies the real hostname, so the connection
     * stays secure. OkHttp tries each address until one connects.
     */
    private val resilientDns = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            return try {
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (error: java.net.UnknownHostException) {
                if (hostname == "resqmesh-api-3g1c.onrender.com") {
                    listOf(16, 18).map { lastOctet ->
                        java.net.InetAddress.getByAddress(
                            hostname,
                            byteArrayOf(216.toByte(), 24, 57, lastOctet.toByte())
                        )
                    }
                } else throw error
            }
        }
    }

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .dns(resilientDns)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private suspend fun rawRequest(
        method: String,
        path: String,
        payload: JSONObject,
        token: String? = null
    ): String = withContext(Dispatchers.IO) {
        val requestBody = if (method in setOf("GET", "HEAD")) {
            null
        } else {
            okhttp3.RequestBody.create(
                "application/json; charset=utf-8".toMediaTypeOrNull(),
                payload.toString()
            )
        }
        val builder = okhttp3.Request.Builder()
            .url("$BASE_URL$path")
            .method(method, requestBody)
        token?.let { builder.header("Authorization", "Bearer $it") }

        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                    throw ResQApiException(response.code, detail?.takeIf(String::isNotBlank) ?: "Server returned HTTP ${response.code}")
                }
                body
            }
        } catch (error: ResQApiException) {
            throw error
        } catch (error: java.io.IOException) {
            throw IllegalStateException("ResQMesh server unreachable (${error.message ?: "network error"}). SMS and Bluetooth SOS layers stay active.", error)
        }
    }
}
