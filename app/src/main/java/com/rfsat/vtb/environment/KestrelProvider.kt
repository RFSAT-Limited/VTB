package com.rfsat.vtb.environment

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.rfsat.vtb.log.Logger
import java.util.UUID

/**
 * Reads environmental data from a PAIRED Kestrel meter over BLE (v17.0) —
 * targets the Kestrel 5700 Elite and the Kestrel DROP D3 logger.
 *
 * HONESTY NOTE ON THE PROTOCOL: Kestrel's GATT layout is proprietary and
 * undocumented. This implementation does two things:
 *
 *  1. ALWAYS: connects to the bonded Kestrel, discovers every service,
 *     reads every readable characteristic, and logs UUIDs + raw hex to
 *     vtb_log.txt. Whatever else happens, one connection attempt gives us
 *     the device's true layout to wire in precisely.
 *
 *  2. BEST-EFFORT: parses the community-reverse-engineered DROP-series
 *     service (UUID base 12630000-cc25-497d-9854-9b6c02c77054):
 *     temperature / humidity / pressure as little-endian scaled integers.
 *     If the device (particularly the 5700 Elite, whose LiNK Ballistics
 *     protocol differs) doesn't expose these, no value is taken and the
 *     phone/default environment stays in force — never a garbage parse.
 *
 * Uses only BONDED devices (pair the Kestrel in Android Bluetooth settings
 * first), so no BLE scan — and therefore no location permission — is
 * needed; only BLUETOOTH_CONNECT on Android 12+.
 */
object KestrelProvider {

    private const val TAG = "KestrelProvider"
    private const val TIMEOUT_MS = 12_000L

    private val DROP_SERVICE: UUID = UUID.fromString("12630000-cc25-497d-9854-9b6c02c77054")
    private val DROP_TEMPERATURE: UUID = UUID.fromString("12630001-cc25-497d-9854-9b6c02c77054")
    private val DROP_HUMIDITY: UUID = UUID.fromString("12630002-cc25-497d-9854-9b6c02c77054")
    private val DROP_PRESSURE: UUID = UUID.fromString("12630003-cc25-497d-9854-9b6c02c77054")
    /** Standard Bluetooth Environmental Sensing service — some firmware
     *  exposes it alongside the proprietary one; parse it when present
     *  (these UUIDs and formats ARE official Bluetooth SIG definitions). */
    private val ESS_SERVICE: UUID = UUID.fromString("0000181a-0000-1000-8000-00805f9b34fb")
    private val ESS_TEMPERATURE: UUID = UUID.fromString("00002a6e-0000-1000-8000-00805f9b34fb") // sint16, 0.01 °C
    private val ESS_HUMIDITY: UUID = UUID.fromString("00002a6f-0000-1000-8000-00805f9b34fb")    // uint16, 0.01 %
    private val ESS_PRESSURE: UUID = UUID.fromString("00002a6d-0000-1000-8000-00805f9b34fb")    // uint32, 0.1 Pa

    /** A bonded device that looks like a Kestrel, or null. */
    @SuppressLint("MissingPermission") // caller checks BLUETOOTH_CONNECT
    fun findPairedKestrel(): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        return runCatching {
            adapter.bondedDevices.firstOrNull { it.name?.contains("kestrel", ignoreCase = true) == true }
        }.getOrNull()
    }

    /**
     * Connect, discover, log everything, best-effort read. Calls [onDone]
     * on the main thread with true if at least one environmental value was
     * obtained (already pushed into [EnvironmentManager]).
     */
    @SuppressLint("MissingPermission")
    fun read(context: Context, device: BluetoothDevice, onDone: (Boolean) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var tempC: Double? = null
        var pressPa: Double? = null
        var humFrac: Double? = null
        var finished = false
        val queue = ArrayDeque<BluetoothGattCharacteristic>()

        fun finish(gatt: BluetoothGatt?) {
            if (finished) return
            finished = true
            runCatching { gatt?.close() }
            val got = tempC != null || pressPa != null || humFrac != null
            if (got) EnvironmentManager.setFromKestrel(tempC, pressPa, humFrac)
            Logger.i(TAG, "Kestrel read done: temp=$tempC °C pressure=$pressPa Pa humidity=$humFrac (got=$got)")
            handler.post { onDone(got) }
        }

        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Logger.i(TAG, "GATT state=$newState status=$status")
                if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) finish(gatt)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                // Discovery log: THE deliverable of the first connection —
                // it pins down this device's real layout for exact wiring.
                for (svc in gatt.services) {
                    Logger.i(TAG, "Kestrel service ${svc.uuid}")
                    for (ch in svc.characteristics) {
                        Logger.i(TAG, "  char ${ch.uuid} props=0x${ch.properties.toString(16)}")
                        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                            queue.add(ch)
                        }
                    }
                }
                readNext(gatt)
            }

            fun readNext(gatt: BluetoothGatt) {
                val ch = queue.removeFirstOrNull() ?: run { finish(gatt); return }
                if (!gatt.readCharacteristic(ch)) readNext(gatt)
            }

            @Deprecated("pre-33 callback; fine at minSdk 26")
            override fun onCharacteristicRead(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
                val v = ch.value ?: ByteArray(0)
                Logger.i(TAG, "  read ${ch.uuid} = ${v.joinToString("") { "%02x".format(it) }}")
                if (status == BluetoothGatt.GATT_SUCCESS && v.isNotEmpty()) parse(ch.uuid, v)
                readNext(gatt)
            }

            fun parse(uuid: UUID, v: ByteArray) {
                fun le16(): Int = (v[0].toInt() and 0xFF) or ((v[1].toInt() and 0xFF) shl 8)
                fun sle16(): Int = le16().let { if (it > 0x7FFF) it - 0x10000 else it }
                fun le32(): Long {
                    var r = 0L
                    for (i in 0 until minOf(4, v.size)) r = r or ((v[i].toLong() and 0xFF) shl (8 * i))
                    return r
                }
                when (uuid) {
                    ESS_TEMPERATURE -> if (v.size >= 2) tempC = sle16() / 100.0
                    ESS_HUMIDITY -> if (v.size >= 2) humFrac = le16() / 10000.0
                    ESS_PRESSURE -> if (v.size >= 4) pressPa = le32() / 10.0
                    DROP_TEMPERATURE -> if (v.size >= 2) {
                        val t = sle16() / 100.0
                        if (t in -60.0..80.0) tempC = t // plausibility gate on the unofficial parse
                    }
                    DROP_HUMIDITY -> if (v.size >= 2) {
                        val h = le16() / 10000.0
                        if (h in 0.0..1.0) humFrac = h
                    }
                    DROP_PRESSURE -> if (v.size >= 4) {
                        val p = le32() / 10.0
                        if (p in 30_000.0..110_000.0) pressPa = p
                    }
                }
            }
        }

        Logger.i(TAG, "Connecting to paired Kestrel \"${device.name}\"")
        val gatt = device.connectGatt(context, false, cb)
        handler.postDelayed({ finish(gatt) }, TIMEOUT_MS)
    }
}
