package com.resqnet.app.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Layer 3 — Bluetooth mesh SOS.
 *
 * The victim's phone broadcasts an iBeacon-format BLE advertisement carrying the
 * emergency's approximate location. Nearby phones running ResQNet detect it even
 * with no internet and no SIM, raise the same screaming alarm, and re-broadcast
 * it with a decremented hop count so the SOS spreads beyond direct radio range.
 *
 * iBeacon layout (manufacturer 0x004C): 0x02 0x15 | UUID(16) | major(2) | minor(2) | txPower(1)
 * major = latitude*100 + 9000 (0.01 degree resolution), minor = longitude*100 + 18000,
 * txPower byte carries the remaining hop count offset by 200.
 */
object SosMesh {
    val SOS_UUID: UUID = UUID.fromString("52455351-4D45-5348-0000-000000000001")
    const val MANUFACTURER_ID = 0x004C
    const val HOPS_MAX = 7
    private const val HOPS_OFFSET = 200
    private const val ALERT_TTL_MS = 10 * 60 * 1000L

    private var appContext: Context? = null
    private val seen = mutableMapOf<Long, Long>()
    private var relayCallback: AdvertiseCallback? = null

    private fun adapter(context: Context): BluetoothAdapter? = try {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }

    private fun ibeaconData(latitude: Double, longitude: Double, hops: Int): ByteArray =
        encodeBeacon(latitude, longitude, hops)

    fun encodeBeacon(latitude: Double, longitude: Double, hops: Int): ByteArray {
        val uuidBytes = ByteArray(16)
        val most = SOS_UUID.mostSignificantBits
        val least = SOS_UUID.leastSignificantBits
        for (i in 0 until 8) uuidBytes[i] = (most shr (8 * (7 - i))).toByte()
        for (i in 0 until 8) uuidBytes[8 + i] = (least shr (8 * (7 - i))).toByte()
        val major = ((latitude * 100).roundToInt() + 9000).coerceIn(0, 65535)
        val minor = ((longitude * 100).roundToInt() + 18000).coerceIn(0, 65535)
        val data = ByteArray(23)
        data[0] = 0x02
        data[1] = 0x15
        uuidBytes.copyInto(data, 2)
        data[18] = (major shr 8).toByte()
        data[19] = (major and 0xFF).toByte()
        data[20] = (minor shr 8).toByte()
        data[21] = (minor and 0xFF).toByte()
        data[22] = (hops + HOPS_OFFSET).toByte()
        return data
    }

    private fun decode(payload: ByteArray?): Triple<Double, Double, Int>? =
        decodeBeacon(payload)

    fun decodeBeacon(payload: ByteArray?): Triple<Double, Double, Int>? {
        if (payload == null || payload.size < 23) return null
        if (payload[0] != 0x02.toByte() || payload[1] != 0x15.toByte()) return null
        val major = ((payload[18].toInt() and 0xFF) shl 8) or (payload[19].toInt() and 0xFF)
        val minor = ((payload[20].toInt() and 0xFF) shl 8) or (payload[21].toInt() and 0xFF)
        val raw = payload[22].toInt() and 0xFF
        val hops = if (raw in (HOPS_OFFSET + 1)..(HOPS_OFFSET + HOPS_MAX)) raw - HOPS_OFFSET else 2
        val latitude = (major - 9000) / 100.0
        val longitude = (minor - 18000) / 100.0
        return Triple(latitude, longitude, hops)
    }

    /** Victim side: broadcast this SOS over BLE for the emergency window. */
    fun startAdvertising(
        context: Context,
        latitude: Double,
        longitude: Double,
        hops: Int = HOPS_MAX,
        durationMs: Long = 10 * 60 * 1000L
    ) {
        try {
            val bluetooth = adapter(context) ?: return
            if (!bluetooth.isEnabled) return
            val advertiser = bluetooth.bluetoothLeAdvertiser ?: return
            val key = ibeaconData(latitude, longitude, hops)
            val identity = (((key[18].toInt() and 0xFF) shl 8) or (key[19].toInt() and 0xFF)).toLong() * 65536 +
                (((key[20].toInt() and 0xFF) shl 8) or (key[21].toInt() and 0xFF))
            seen[identity] = System.currentTimeMillis()
            appContext = context.applicationContext
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTimeout(durationMs.toInt())
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()
            val data = AdvertiseData.Builder()
                .addManufacturerData(MANUFACTURER_ID, key)
                .setIncludeDeviceName(false)
                .build()
            advertiser.startAdvertising(settings, data, object : AdvertiseCallback() {})
        } catch (_: Exception) {
        }
    }

    /** Responder side: listen for BLE SOS beacons (works with zero network). */
    fun startListening(context: Context) {
        try {
            appContext = context.applicationContext
            val bluetooth = adapter(context) ?: return
            if (!bluetooth.isEnabled) return
            val scanner = bluetooth.bluetoothLeScanner ?: return
            val template = ibeaconData(0.0, 0.0, 1)
            val mask = ByteArray(template.size) { if (it in 2..17) 0xFF.toByte() else 0x00 }
            val filter = ScanFilter.Builder()
                .setManufacturerData(MANUFACTURER_ID, template, mask)
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (_: Exception) {
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val payload: ByteArray? = try {
                result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID)
            } catch (_: SecurityException) {
                null
            }
            appContext?.let { onBeaconPayload(it, payload) }
        }
    }

    /** Shared beacon-handling path used by the BLE scanner and the emulator test hook. */
    fun onBeaconPayload(context: Context, payload: ByteArray?) {
        val (latitude, longitude, hops) = decode(payload) ?: run { Log.d("ResQMesh", "decode failed: payload=${payload?.size}"); return }
        val major = ((latitude * 100).roundToInt() + 9000)
        val minor = ((longitude * 100).roundToInt() + 18000)
        val key = major.toLong() * 65536 + minor
        val now = System.currentTimeMillis()
        if (now - (seen[key] ?: 0L) < ALERT_TTL_MS) { Log.d("ResQMesh", "deduped key=$key"); return }
        seen[key] = now

        EmergencyStore.post(
            EmergencyStore.EmergencyInfo(
                requester = "Nearby device (Bluetooth)",
                latitude = latitude,
                longitude = longitude,
                distanceKm = 0.0,
                receivedAt = now
            )
        )
        Log.d("ResQMesh", "mesh alert lat=$latitude lon=$longitude hops=$hops")
            SosAlarm.show(
            context,
            "🚨 EMERGENCY SOS! (via Bluetooth)",
            "Someone nearby needs urgent help! 📍 Approx location: %.2f, %.2f".format(Locale.US, latitude, longitude)
        )

        // Mesh relay: pass the SOS onward with one hop less.
        if (hops > 1) {
            try {
                val bluetooth = adapter(context) ?: return
                val advertiser = bluetooth.bluetoothLeAdvertiser ?: return
                relayCallback?.let { advertiser.stopAdvertising(it) }
                val relay = object : AdvertiseCallback() {}
                relayCallback = relay
                val settings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setConnectable(false)
                    .setTimeout(60 * 1000)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .build()
                val data = AdvertiseData.Builder()
                    .addManufacturerData(MANUFACTURER_ID, ibeaconData(latitude, longitude, hops - 1))
                    .setIncludeDeviceName(false)
                    .build()
                advertiser.startAdvertising(settings, data, relay)
            } catch (_: Exception) {
            }
        }
    }
}

/** Emulator test hook: injects a synthetic ResQMesh SOS beacon into the full mesh path. */
class SosMeshTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.resqnet.app.TEST_MESH_BEACON") return
        Log.d("ResQMesh", "TEST beacon received")
        val latitude = intent.getDoubleExtra("lat", Double.MAX_VALUE)
        val longitude = intent.getDoubleExtra("lon", Double.MAX_VALUE)
        if (latitude == Double.MAX_VALUE || longitude == Double.MAX_VALUE) return
        SosMesh.onBeaconPayload(context, SosMesh.encodeBeacon(latitude, longitude, 7))
    }
}
