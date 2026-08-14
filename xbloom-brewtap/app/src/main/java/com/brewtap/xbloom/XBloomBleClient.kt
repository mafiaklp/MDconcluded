package com.brewtap.xbloom

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

class XBloomBleClient(private val context: Context) {
    interface Callback {
        fun onState(message: String)
        fun onLoaded(deviceName: String)
        fun onError(message: String)
    }

    companion object {
        const val DEFAULT_TAP_RSSI = -58
        fun isNearEnough(rssi: Int, threshold: Int = DEFAULT_TAP_RSSI): Boolean = rssi >= threshold
    }

    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var scanner = adapter?.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null
    private var callback: Callback? = null
    private var recipe: BrewRecipe? = null
    private var deviceName = "xBloom"
    private var proximityMode = false
    private var rssiThreshold = DEFAULT_TAP_RSSI
    private var connectStarted = false
    private var sawXBloom = false

    private val writeUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    fun sendRecipe(recipe: BrewRecipe, callback: Callback) {
        beginScan(recipe, callback, proximity = false, threshold = Int.MIN_VALUE)
    }

    @SuppressLint("MissingPermission")
    fun sendRecipeWhenNear(
        recipe: BrewRecipe,
        callback: Callback,
        threshold: Int = DEFAULT_TAP_RSSI,
    ) {
        beginScan(recipe, callback, proximity = true, threshold = threshold)
    }

    @SuppressLint("MissingPermission")
    private fun beginScan(
        recipe: BrewRecipe,
        callback: Callback,
        proximity: Boolean,
        threshold: Int,
    ) {
        closeGattOnly()
        this.recipe = recipe
        this.callback = callback
        this.proximityMode = proximity
        this.rssiThreshold = threshold
        this.connectStarted = false
        this.sawXBloom = false

        if (adapter == null || !adapter.isEnabled) {
            callback.onError("Bluetooth is off or unavailable.")
            return
        }

        callback.onState(
            if (proximity) "READY TO TAP\nMove your phone close to the xBloom NFC area."
            else "Searching for xBloom…"
        )
        scanner = adapter.bluetoothLeScanner
        scanner?.startScan(scanCallback) ?: run {
            callback.onError("Bluetooth scanner unavailable.")
            return
        }

        handler.postDelayed({
            if (connectStarted) return@postDelayed
            try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
            val msg = if (proximity && sawXBloom) {
                "xBloom was found, but the phone did not get close enough. Try again and hold the phone against the NFC area."
            } else {
                "xBloom not found. Make sure the machine is awake and disconnect the official xBloom app, then try again."
            }
            callback.onError(msg)
        }, 15000)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        closeGattOnly()
    }

    @SuppressLint("MissingPermission")
    private fun closeGattOnly() {
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (connectStarted) return
            val name = result.device.name ?: result.scanRecord?.deviceName ?: return
            if (!name.startsWith("XBLOOM", ignoreCase = true)) return

            sawXBloom = true
            deviceName = name

            if (proximityMode && !isNearEnough(result.rssi, rssiThreshold)) {
                callback?.onState(
                    "xBloom detected · ${result.rssi} dBm\nMove the phone closer to the NFC area…"
                )
                return
            }

            connectStarted = true
            scanner?.stopScan(this)
            callback?.onState(
                if (proximityMode) "TAP PROXIMITY DETECTED\nConnecting to $name…"
                else "Connecting to $name…"
            )
            gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            callback?.onError("Bluetooth scan failed ($errorCode).")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                callback?.onError("Connection failed ($status). Disconnect the official xBloom app and retry.")
                closeGattOnly()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                callback?.onState("Connected. Preparing recipe…")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                callback?.onError("Could not discover xBloom services.")
                return
            }
            val ch = g.services.asSequence()
                .flatMap { it.characteristics.asSequence() }
                .firstOrNull { it.uuid == writeUuid }
            val r = recipe
            if (ch == null || r == null) {
                callback?.onError("xBloom command channel not found.")
                return
            }

            callback?.onState("Sending adaptive recipe…")
            val packets = listOf(
                XBloomBleProtocol.buildHandshake(),
                XBloomBleProtocol.buildBackHome(),
                XBloomBleProtocol.buildBypass(r),
                XBloomBleProtocol.buildSetCup(),
                XBloomBleProtocol.buildDirectRecipePacket(r),
            )
            writePackets(g, ch, packets, 0)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writePackets(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        packets: List<ByteArray>,
        index: Int,
    ) {
        if (index >= packets.size) {
            callback?.onLoaded(deviceName)
            handler.postDelayed({ closeGattOnly() }, 1200)
            return
        }
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ch.value = packets[index]
        val ok = g.writeCharacteristic(ch)
        if (!ok) {
            callback?.onError("Recipe transfer failed at step ${index + 1}.")
            closeGattOnly()
            return
        }
        handler.postDelayed({ writePackets(g, ch, packets, index + 1) }, 450)
    }
}
